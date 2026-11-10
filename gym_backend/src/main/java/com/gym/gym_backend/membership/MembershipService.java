package com.gym.gym_backend.membership;

import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class MembershipService {
    private final SupabaseDataClient db;
    private final Clock clock;

    public MembershipService(SupabaseDataClient db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    public List<Map<String, Object>> forMember(long memberId) {
        return enrich(db.select("membership", Map.of("member_id", "eq." + memberId), "start_date.desc", 200,
                "*,membership_plan(name,duration_months,benefits)"));
    }

    public Map<String, Object> register(Map<String, Object> b, boolean renewal) {
        long memberId = number(b, "memberId"), planId = number(b, "planId");
        Map<String, Object> member = one("member", "member_id", memberId, "Không tìm thấy hội viên");
        if (!"Active".equals(member.get("status")))
            throw ApiException.badRequest("Hội viên không ở trạng thái Active");
        Map<String, Object> plan = one("membership_plan", "plan_id", planId, "Không tìm thấy gói hội viên");
        if (!"Available".equals(plan.get("status")))
            throw ApiException.badRequest("Chỉ gói Available mới được đăng ký");
        List<Map<String, Object>> all = db.select("membership", Map.of("member_id", "eq." + memberId), "end_date.desc",
                200);
        Map<String, Object> latest = all.isEmpty() ? null : all.getFirst();
        LocalDate start;
        Long previous = null;
        if (!renewal) {
            if (latest != null)
                throw ApiException.conflict("Hội viên đã có kỳ hội viên; vui lòng dùng luồng gia hạn nếu đủ điều kiện");
            start = date(b, "startDate");
            if (start.isBefore(today()))
                throw ApiException.badRequest("startDate không được trước ngày đăng ký");
        } else {
            if (latest == null)
                throw ApiException.badRequest("Hội viên chưa có kỳ hội viên để gia hạn");
            LocalDate lastStart = LocalDate.parse(latest.get("start_date").toString()),
                    lastEnd = LocalDate.parse(latest.get("end_date").toString());
            if (today().isBefore(lastStart))
                throw ApiException.badRequest("Kỳ hội viên mới nhất đang ở trạng thái Upcoming; không thể gia hạn");
            if (!today().isAfter(lastEnd)) {
                long remaining = ChronoUnit.DAYS.between(today(), lastEnd);
                if (remaining > 7)
                    throw ApiException.badRequest("Chỉ được gia hạn khi còn tối đa 7 ngày hoặc đã hết hạn");
                start = lastEnd.plusDays(1);
            } else {
                start = date(b, "startDate");
                if (start.isBefore(today()))
                    throw ApiException.badRequest("startDate không được trước ngày đăng ký");
            }
            previous = ((Number) latest.get("membership_id")).longValue();
        }
        int months = ((Number) plan.get("duration_months")).intValue();
        LocalDate end = start.plusMonths(months).minusDays(1);
        Map<String, String> overlap = new HashMap<>();
        overlap.put("member_id", "eq." + memberId);
        overlap.put("start_date", "lte." + end);
        overlap.put("end_date", "gte." + start);
        if (!db.select("membership", overlap, null, 1).isEmpty())
            throw ApiException.conflict("Kỳ hội viên bị trùng với một kỳ hiện có");
        Map<String, Object> values = new HashMap<>();
        values.put("member_id", memberId);
        values.put("plan_id", planId);
        values.put("start_date", start.toString());
        values.put("end_date", end.toString());
        values.put("price_at_registration", plan.get("price"));
        if (previous != null)
            values.put("previous_membership_id", previous);
        return enrich(db.insert("membership", values)).getFirst();
    }

    public List<Map<String, Object>> expiring() {
        LocalDate now = today();
        Map<String, String> f = Map.of("and", "(end_date.gte." + now.minusDays(30) + ",end_date.lte." + now.plusDays(7)
                + ",start_date.lte." + now + ")");
        List<Map<String, Object>> candidates = db.select("membership", f, "end_date.asc", 500,
                "*,member(full_name,phone),membership_plan(name)");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : candidates) {
            LocalDate end = LocalDate.parse(row.get("end_date").toString());
            long remaining = ChronoUnit.DAYS.between(now, end);
            boolean near = remaining >= 0 && remaining <= 7;
            boolean recent = remaining < 0 && remaining >= -30;
            if (!near && !recent)
                continue;
            long memberId = ((Number) row.get("member_id")).longValue();
            boolean followed = db.select("membership", Map.of("member_id", "eq." + memberId, "start_date", "gt." + end),
                    "start_date.asc", 1).size() > 0;
            if (followed)
                continue;
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("membershipStatus", near ? "Active" : "Expired");
            out.put("nearExpiration", near);
            out.put("daysRemaining", remaining);
            result.add(out);
        }
        return result;
    }

    private List<Map<String, Object>> enrich(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate n = today();
        for (Map<String, Object> r : rows) {
            Map<String, Object> x = new LinkedHashMap<>(r);
            LocalDate s = LocalDate.parse(r.get("start_date").toString()),
                    e = LocalDate.parse(r.get("end_date").toString());
            String status = n.isBefore(s) ? "Upcoming" : n.isAfter(e) ? "Expired" : "Active";
            long days = ChronoUnit.DAYS.between(n, e);
            x.put("membershipStatus", status);
            x.put("nearExpiration", status.equals("Active") && days <= 7);
            x.put("daysRemaining", days);
            out.add(x);
        }
        return out;
    }

    private Map<String, Object> one(String table, String key, long id, String error) {
        return db.select(table, Map.of(key, "eq." + id), null, 1).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound(error));
    }

    private long number(Map<String, Object> b, String k) {
        try {
            return Long.parseLong(b.get(k).toString());
        } catch (Exception e) {
            throw ApiException.badRequest(k + " là trường bắt buộc và phải là số nguyên");
        }
    }

    private LocalDate date(Map<String, Object> b, String k) {
        try {
            return LocalDate.parse(b.get(k).toString());
        } catch (Exception e) {
            throw ApiException.badRequest(k + " cần có định dạng YYYY-MM-DD");
        }
    }

}
