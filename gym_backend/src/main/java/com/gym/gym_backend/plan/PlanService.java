package com.gym.gym_backend.plan;

import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.common.util.Input;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class PlanService {
    private final SupabaseDataClient db;

    public PlanService(SupabaseDataClient db) {
        this.db = db;
    }

    public List<Map<String, Object>> list(String status) {
        return db.select("membership_plan",
                status == null || status.isBlank() ? Map.of() : Map.of("status", "eq." + status), "name.asc", 200);
    }

    public Map<String, Object> get(long id) {
        return one(id);
    }

    public Map<String, Object> create(Map<String, Object> b) {
        Map<String, Object> v = values(b, true);
        v.put("status", "Available");
        return db.insert("membership_plan", v).getFirst();
    }

    public Map<String, Object> update(long id, Map<String, Object> b) {
        one(id);
        Map<String, Object> v = values(b, false);
        if (v.isEmpty())
            throw ApiException.badRequest("Không có trường nào để cập nhật");
        return db.update("membership_plan", "plan_id", id, v).getFirst();
    }

    public Map<String, Object> status(long id, String status) {
        if (status == null || !Set.of("Available", "Unavailable").contains(status))
            throw ApiException.badRequest("status chỉ nhận Available hoặc Unavailable");
        one(id);
        return db.update("membership_plan", "plan_id", id, Map.of("status", status)).getFirst();
    }

    private Map<String, Object> one(long id) {
        return db.select("membership_plan", Map.of("plan_id", "eq." + id), null, 1).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy gói hội viên"));
    }

    private Map<String, Object> values(Map<String, Object> b, boolean create) {
        Map<String, Object> v = new LinkedHashMap<>();
        if (b.containsKey("name") || create)
            v.put("name", Input.text(b, "name", true, 100));
        if (b.containsKey("durationMonths") || create) {
            int d = Input.integer(b, "durationMonths", true);
            if (d < 1)
                throw ApiException.badRequest("durationMonths phải lớn hơn 0");
            v.put("duration_months", d);
        }
        if (b.containsKey("price") || create) {
            var p = Input.money(b, "price");
            if (p.signum() <= 0)
                throw ApiException.badRequest("price phải lớn hơn 0");
            v.put("price", p);
        }
        if (b.containsKey("benefits") || create)
            v.put("benefits", Input.text(b, "benefits", true, 1000));
        return v;
    }

}
