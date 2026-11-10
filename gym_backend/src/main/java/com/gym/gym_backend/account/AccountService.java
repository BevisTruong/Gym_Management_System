package com.gym.gym_backend.account;

import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.common.util.Input;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.*;

@Service
public class AccountService {
    private final SupabaseDataClient db;
    private final PasswordEncoder encoder;
    private static final SecureRandom RANDOM = new SecureRandom();

    public AccountService(SupabaseDataClient d, PasswordEncoder e) {
        db = d;
        encoder = e;
    }

    public List<Map<String, Object>> list(String role, String status) {
        Map<String, String> f = new HashMap<>();
        if (role != null)
            f.put("role", "eq." + role);
        if (status != null)
            f.put("status", "eq." + status);
        return safe(db.select("account", f, "account_id.asc", 300));
    }

    public Map<String, Object> create(Map<String, Object> b) {
        String username = Input.text(b, "username", true, 50), name = Input.text(b, "fullName", true, 100),
                role = Input.text(b, "role", true, 10);
        if (!Set.of("Admin", "Staff").contains(role))
            throw ApiException.badRequest("Admin chỉ có thể tạo tài khoản Admin hoặc Staff tại đây");
        String temp = password();
        Map<String, Object> v = new HashMap<>();
        v.put("username", username);
        v.put("full_name", name);
        v.put("password_hash", encoder.encode(temp));
        v.put("role", role);
        v.put("status", "Active");
        v.put("must_change_password", true);
        Map<String, Object> created = db.insert("account", v).getFirst();
        created.remove("password_hash");
        created.put("temporaryPassword", temp);
        return created;
    }

    public Map<String, Object> status(long actorAccountId, long id, String status) {
        Map<String, Object> old = one(id);
        if (status == null || !Set.of("Active", "Locked").contains(status))
            throw ApiException.badRequest("status chỉ nhận Active hoặc Locked");
        if (actorAccountId == id && "Locked".equals(status))
            throw ApiException.conflict("Không thể khóa tài khoản Admin đang đăng nhập");
        if ("Admin".equals(old.get("role")) && "Active".equals(old.get("status")) && "Locked".equals(status)
                && activeAdmins() <= 1)
            throw ApiException.conflict("Hệ thống phải luôn có ít nhất một Admin đang hoạt động");
        return safeOne(db.update("account", "account_id", id, Map.of("status", status)).getFirst());
    }

    public Map<String, Object> reset(long id) {
        one(id);
        String temp = password();
        Map<String, Object> v = Map.of("password_hash", encoder.encode(temp), "must_change_password", true);
        Map<String, Object> r = safeOne(db.update("account", "account_id", id, v).getFirst());
        r.put("temporaryPassword", temp);
        return r;
    }

    private long activeAdmins() {
        return db.select("account", Map.of("role", "eq.Admin", "status", "eq.Active"), null, 2).size();
    }

    private Map<String, Object> one(long id) {
        return db.select("account", Map.of("account_id", "eq." + id), null, 1).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
    }

    private List<Map<String, Object>> safe(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows)
            out.add(safeOne(r));
        return out;
    }

    private Map<String, Object> safeOne(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.remove("password_hash");
        return out;
    }

    private String password() {
        byte[] b = new byte[18];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b) + "C5!";
    }

}
