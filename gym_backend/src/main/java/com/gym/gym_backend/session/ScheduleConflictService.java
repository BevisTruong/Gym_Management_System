package com.gym.gym_backend.session;

import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScheduleConflictService {
    private final SupabaseDataClient db;

    public ScheduleConflictService(SupabaseDataClient db) {
        this.db = db;
    }

    public void ensureNoConflicts(long memberId, long trainerId, LocalDate date, LocalTime start, LocalTime end,
            Long excludedSessionId) {
        if (hasConflict("trainer_id", trainerId, date, start, end, excludedSessionId))
            throw ApiException.conflict("Huấn luyện viên đã có lịch trong khoảng thời gian này");
        if (hasConflict("member_id", memberId, date, start, end, excludedSessionId))
            throw ApiException.conflict("Hội viên đã có lịch trong khoảng thời gian này");
    }

    public List<Map<String, Object>> availableTrainers(LocalDate date, LocalTime start, LocalTime end) {
        List<Map<String, Object>> activeTrainers = db.select("trainer", Map.of("status", "eq.Active"),
                "full_name.asc", 200);
        List<Map<String, Object>> available = new ArrayList<>();

        for (Map<String, Object> trainer : activeTrainers) {
            long trainerId = ((Number) trainer.get("trainer_id")).longValue();
            if (!hasConflict("trainer_id", trainerId, date, start, end, null))
                available.add(trainer);
        }
        return available;
    }

    public boolean hasConflict(String identityColumn, long identity, LocalDate date, LocalTime start, LocalTime end,
            Long excludedSessionId) {
        if (!identityColumn.equals("trainer_id") && !identityColumn.equals("member_id"))
            throw new IllegalArgumentException("Unsupported schedule identity column");

        Map<String, String> filters = new HashMap<>();
        filters.put(identityColumn, "eq." + identity);
        filters.put("session_date", "eq." + date);
        filters.put("status", "eq.Scheduled");
        filters.put("start_time", "lt." + end);
        filters.put("end_time", "gt." + start);
        if (excludedSessionId != null)
            filters.put("session_id", "neq." + excludedSessionId);

        return !db.select("training_session", filters, null, 1).isEmpty();
    }
}
