package com.gym.gym_backend.trainer;

import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.common.util.Input;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

@Service
public class TrainerService {
    private final SupabaseDataClient db;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private static final SecureRandom RANDOM = new SecureRandom();

    public TrainerService(SupabaseDataClient db, PasswordEncoder encoder, Clock clock) {
        this.db = db;
        this.encoder = encoder;
        this.clock = clock;
    }

    public List<Map<String, Object>> list(String search, String status, int limit) {
        Map<String, String> f = new HashMap<>();
        if (status != null && !status.isBlank())
            f.put("status", "eq." + status);
        if (search != null && !search.isBlank())
            f.put("full_name", "ilike.*" + search.trim() + "*");
        return db.select("trainer", f, "full_name.asc", Math.min(Math.max(limit, 1), 200));
    }

    public Map<String, Object> get(long id) {
        return one(id);
    }

    public Map<String, Object> create(Map<String, Object> b) {
        Map<String, Object> v = values(b, true);
        v.put("status", "Active");
        Map<String, Object> trainer = db.insert("trainer", v).getFirst();
        String temp = password();
        try {
            Map<String, Object> a = new HashMap<>();
            a.put("username", trainer.get("phone"));
            a.put("full_name", trainer.get("full_name"));
            a.put("password_hash", encoder.encode(temp));
            a.put("role", "Trainer");
            a.put("status", "Active");
            a.put("must_change_password", true);
            a.put("trainer_id", trainer.get("trainer_id"));
            db.insert("account", a);
        } catch (RuntimeException e) {
            db.delete("trainer", "trainer_id", trainer.get("trainer_id"));
            throw e;
        }
        Map<String, Object> out = new LinkedHashMap<>(trainer);
        out.put("temporaryPassword", temp);
        return out;
    }

    public Map<String, Object> update(long id, Map<String, Object> b) {
        one(id);
        Map<String, Object> v = values(b, false);
        if (v.isEmpty())
            throw ApiException.badRequest("Không có trường nào để cập nhật");
        return db.rpc("update_trainer_profile", Map.of("p_id", id, "p_values", v)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy huấn luyện viên"));
    }

    public Map<String, Object> status(long id, String status) {
        if (status == null || !Set.of("Active", "Inactive").contains(status))
            throw ApiException.badRequest("status chỉ nhận Active hoặc Inactive");
        one(id);
        db.update("trainer", "trainer_id", id, Map.of("status", status));
        db.update("account", "trainer_id", id, Map.of("status", status.equals("Active") ? "Active" : "Locked"));
        Map<String, String> f = new HashMap<>();
        f.put("trainer_id", "eq." + id);
        f.put("status", "eq.Scheduled");
        f.put("session_date", "gte." + LocalDate.now(clock));
        List<Map<String, Object>> affected = status.equals("Inactive")
                ? db.select("training_session", f, "session_date.asc,start_time.asc", 200)
                : List.of();
        Map<String, Object> result = new LinkedHashMap<>(one(id));
        result.put("affectedScheduledSessions", affected);
        return result;
    }

    private Map<String, Object> one(long id) {
        return db.select("trainer", Map.of("trainer_id", "eq." + id), null, 1).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy huấn luyện viên"));
    }

    private Map<String, Object> values(Map<String, Object> b, boolean create) {
        Map<String, Object> v = new LinkedHashMap<>();
        put(v, b, "fullName", "full_name", true, 100, create);
        if (b.containsKey("phone") || create) {
            String p = Input.text(b, "phone", true, 20);
            Input.phone(p);
            v.put("phone", p);
        }
        if (b.containsKey("email") || create) {
            String e = Input.text(b, "email", true, 100);
            Input.email(e, true);
            v.put("email", e);
        }
        put(v, b, "specialization", "specialization", false, 100, false);
        if (b.containsKey("yearsOfExperience")) {
            int y = Input.integer(b, "yearsOfExperience", true);
            if (y < 0)
                throw ApiException.badRequest("yearsOfExperience không được âm");
            v.put("years_of_experience", y);
        }
        return v;
    }

    private void put(Map<String, Object> v, Map<String, Object> b, String from, String to, boolean req, int max,
            boolean create) {
        if (b.containsKey(from) || create && req)
            v.put(to, Input.text(b, from, req, max));
    }

    private String password() {
        byte[] b = new byte[18];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b) + "B7!";
    }

}
