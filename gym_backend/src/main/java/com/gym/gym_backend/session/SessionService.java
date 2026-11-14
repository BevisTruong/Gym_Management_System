package com.gym.gym_backend.session;

import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.common.util.Input;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class SessionService {
    private static final LocalTime OPEN = LocalTime.of(5, 0), CLOSE = LocalTime.of(22, 0);
    private final SupabaseDataClient db;
    private final ScheduleConflictService scheduleConflicts;
    private final Clock clock;

    public SessionService(SupabaseDataClient db, ScheduleConflictService scheduleConflicts, Clock clock) {
        this.db = db;
        this.scheduleConflicts = scheduleConflicts;
        this.clock = clock;
    }

    public List<Map<String, Object>> list(String date, String member, String trainer, String status, int limit) {
        Map<String, String> f = new HashMap<>();
        if (date != null)
            f.put("session_date", "eq." + date);
        if (member != null)
            f.put("member_id", "eq." + member);
        if (trainer != null)
            f.put("trainer_id", "eq." + trainer);
        if ("Scheduled".equals(status) || "Completed".equals(status)) {
            LocalDateTime now = LocalDateTime.now(clock);
            String comparison = "Scheduled".equals(status) ? "gt" : "lte";
            String dateComparison = "Scheduled".equals(status) ? "gt" : "lt";
            f.put("status", "eq.Scheduled");
            f.put("or", "(session_date." + dateComparison + "." + now.toLocalDate()
                    + ",and(session_date.eq." + now.toLocalDate() + ",end_time." + comparison + "."
                    + now.toLocalTime() + "))");
        } else if (status != null) {
            f.put("status", "eq." + status);
        }
        List<Map<String, Object>> rows = withCalculated(
                db.select("training_session", f, "session_date.asc,start_time.asc", Math.min(Math.max(limit, 1), 500),
                        "*,member(full_name,phone,training_objective),trainer(full_name)"));
        return status == null ? rows : rows.stream().filter(r -> status.equals(r.get("displayStatus"))).toList();
    }

    public Map<String, Object> create(Map<String, Object> b) {
        Map<String, Object> v = values(b);
        validate(v, null);
        return withCalculated(db.insert("training_session", v)).getFirst();
    }

    public Map<String, Object> update(long id, Map<String, Object> b) {
        Map<String, Object> old = one(id);
        if (!"Scheduled".equals(old.get("status")) || started(old))
            throw ApiException.conflict("Chỉ có thể sửa buổi Scheduled chưa bắt đầu");
        Map<String, Object> merged = new HashMap<>();
        merged.put("memberId", old.get("member_id"));
        merged.put("trainerId", old.get("trainer_id"));
        merged.put("sessionDate", old.get("session_date"));
        merged.put("startTime", old.get("start_time"));
        merged.put("endTime", old.get("end_time"));
        merged.put("note", old.get("note"));
        merged.putAll(b);
        Map<String, Object> v = values(merged);
        validate(v, id);
        return withCalculated(db.update("training_session", "session_id", id, v)).getFirst();
    }

    public Map<String, Object> cancel(long id) {
        Map<String, Object> old = one(id);
        if (!"Scheduled".equals(old.get("status")) || started(old))
            throw ApiException.conflict("Chỉ có thể hủy buổi Scheduled chưa bắt đầu");
        return db.update("training_session", "session_id", id, Map.of("status", "Cancelled")).getFirst();
    }

    public List<Map<String, Object>> availableTrainers(Map<String, Object> b) {
        LocalDate d = date(b, "sessionDate");
        LocalTime s = time(b, "startTime"), e = time(b, "endTime");
        validateClock(d, s, e);
        return scheduleConflicts.availableTrainers(d, s, e);
    }

    public List<Map<String, Object>> trainerSchedule(long trainerId) {
        return withCalculated(db.select("training_session", Map.of("trainer_id", "eq." + trainerId),
                "session_date.asc,start_time.asc", 500, "*,member(full_name,training_objective)"));
    }

    public List<Map<String, Object>> memberSchedule(long memberId) {
        return withCalculated(db.select("training_session", Map.of("member_id", "eq." + memberId),
                "session_date.desc,start_time.desc", 500, "*,trainer(full_name)"));
    }

    private Map<String, Object> one(long id) {
        return db.select("training_session", Map.of("session_id", "eq." + id), null, 1).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy buổi tập"));
    }

    private boolean started(Map<String, Object> r) {
        LocalDate d = LocalDate.parse(r.get("session_date").toString());
        LocalTime t = LocalTime.parse(r.get("start_time").toString());
        LocalDateTime start = LocalDateTime.of(d, t);
        return !start.isAfter(LocalDateTime.now(clock));
    }

    private Map<String, Object> values(Map<String, Object> b) {
        long member = number(b, "memberId"), trainer = number(b, "trainerId");
        LocalDate d = date(b, "sessionDate");
        LocalTime s = time(b, "startTime"), e = time(b, "endTime");
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("member_id", member);
        v.put("trainer_id", trainer);
        v.put("session_date", d.toString());
        v.put("start_time", s.toString());
        v.put("end_time", e.toString());
        if (b.containsKey("note"))
            v.put("note", Input.text(b, "note", false, 1000));
        return v;
    }

    private void validate(Map<String, Object> v, Long exclude) {
        LocalDate d = LocalDate.parse(v.get("session_date").toString());
        LocalTime s = LocalTime.parse(v.get("start_time").toString()),
                e = LocalTime.parse(v.get("end_time").toString());
        validateClock(d, s, e);
        long member = ((Number) v.get("member_id")).longValue(), trainer = ((Number) v.get("trainer_id")).longValue();
        Map<String, Object> m = one("member", "member_id", member, "Không tìm thấy hội viên");
        if (!"Active".equals(m.get("status")))
            throw ApiException.badRequest("Hội viên phải ở trạng thái Active");
        Map<String, Object> t = one("trainer", "trainer_id", trainer, "Không tìm thấy huấn luyện viên");
        if (!"Active".equals(t.get("status")))
            throw ApiException.badRequest("Huấn luyện viên phải ở trạng thái Active");
        Map<String, String> elig = Map.of("member_id", "eq." + member, "start_date", "lte." + d, "end_date",
                "gte." + d);
        if (db.select("membership", elig, null, 1).isEmpty())
            throw ApiException.badRequest("Hội viên không có membership hợp lệ vào ngày tập");
        scheduleConflicts.ensureNoConflicts(member, trainer, d, s, e, exclude);
    }

    private void validateClock(LocalDate d, LocalTime s, LocalTime e) {
        LocalDate today = LocalDate.now(clock);
        if (d.isBefore(today))
            throw ApiException.badRequest("sessionDate phải là hôm nay hoặc ngày sau");
        if (d.equals(today) && !s.isAfter(LocalTime.now(clock)))
            throw ApiException.badRequest("Buổi tập hôm nay phải bắt đầu sau thời điểm hiện tại");
        if (s.isBefore(OPEN) || e.isAfter(CLOSE) || !e.isAfter(s))
            throw ApiException.badRequest("Khung giờ phải nằm trong 05:00-22:00 và giờ kết thúc sau giờ bắt đầu");
        long mins = Duration.between(s, e).toMinutes();
        if (mins < 30 || mins > 120)
            throw ApiException.badRequest("Thời lượng buổi tập phải từ 30 đến 120 phút");
    }

    private List<Map<String, Object>> withCalculated(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);
        for (Map<String, Object> r : rows) {
            Map<String, Object> x = new LinkedHashMap<>(r);
            String status = String.valueOf(r.get("status"));
            if (status.equals("Scheduled")) {
                LocalDate d = LocalDate.parse(r.get("session_date").toString());
                LocalTime e = LocalTime.parse(r.get("end_time").toString());
                if (!LocalDateTime.of(d, e).isAfter(now))
                    status = "Completed";
            }
            x.put("displayStatus", status);
            out.add(x);
        }
        return out;
    }

    private LocalDate date(Map<String, Object> b, String k) {
        try {
            return LocalDate.parse(b.get(k).toString());
        } catch (Exception e) {
            throw ApiException.badRequest(k + " cần có định dạng YYYY-MM-DD");
        }
    }

    private LocalTime time(Map<String, Object> b, String k) {
        try {
            return LocalTime.parse(b.get(k).toString());
        } catch (Exception e) {
            throw ApiException.badRequest(k + " cần có định dạng HH:mm");
        }
    }

    private long number(Map<String, Object> b, String k) {
        try {
            return Long.parseLong(b.get(k).toString());
        } catch (Exception e) {
            throw ApiException.badRequest(k + " là trường bắt buộc và phải là số nguyên");
        }
    }

    private Map<String, Object> one(String table, String key, long id, String message) {
        return db.select(table, Map.of(key, "eq." + id), null, 1).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound(message));
    }

}
