package com.gym.gym_backend.member;

import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.common.util.Input;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.Clock;
import java.util.*;

@Service
public class MemberService {
    private final SupabaseDataClient db;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private static final SecureRandom RANDOM = new SecureRandom();

    public MemberService(SupabaseDataClient db, PasswordEncoder encoder, Clock clock) {
        this.db = db;
        this.encoder = encoder;
        this.clock = clock;
    }

    public List<Map<String, Object>> list(String search, String status, int limit) {
        Map<String, String> filter = new LinkedHashMap<>();
        if (status != null && !status.isBlank())
            filter.put("status", "eq." + status);
        if (search != null && !search.isBlank()) {
            String q = search.trim().replaceAll("[^\\p{L}\\p{N} @._+()-]", " ");
            String alternatives = "full_name.ilike.*" + q + "*,phone.ilike.*" + q + "*";
            if (q.matches("\\d+"))
                alternatives = "member_id.eq." + q + "," + alternatives;
            filter.put("or", "(" + alternatives + ")");
        }
        return db.select("member", filter, "full_name.asc", Math.min(Math.max(limit, 1), 200));
    }

    public Map<String, Object> get(long id) {
        return one(id);
    }

    public Map<String, Object> create(Map<String, Object> body) {
        Map<String, Object> values = profile(body, true);
        if (!LocalDate.parse(values.get("date_of_birth").toString()).isBefore(LocalDate.now(clock)))
            throw ApiException.badRequest("date_of_birth phải là ngày trong quá khứ");
        List<Map<String, Object>> dup = db.select("member", Map.of("full_name", "ilike." + values.get("full_name"),
                "date_of_birth", "eq." + values.get("date_of_birth")), null, 5);
        if (!dup.isEmpty() && !Boolean.TRUE.equals(body.get("confirmPossibleDuplicate")))
            throw ApiException.conflict(
                    "Có hồ sơ trùng họ tên và ngày sinh; kiểm tra hồ sơ hiện có rồi gửi lại với confirmPossibleDuplicate=true nếu vẫn muốn tạo");
        Map<String, Object> member = db.insert("member", values).getFirst();
        String temporaryPassword = randomPassword();
        try {
            Map<String, Object> account = new HashMap<>();
            account.put("username", member.get("phone"));
            account.put("full_name", member.get("full_name"));
            account.put("password_hash", encoder.encode(temporaryPassword));
            account.put("role", "Member");
            account.put("status", "Active");
            account.put("must_change_password", true);
            account.put("member_id", member.get("member_id"));
            db.insert("account", account);
        } catch (RuntimeException ex) {
            db.delete("member", "member_id", member.get("member_id"));
            throw ex;
        }
        Map<String, Object> result = new LinkedHashMap<>(member);
        result.put("temporaryPassword", temporaryPassword);
        return result;
    }

    public Map<String, Object> update(long id, Map<String, Object> body) {
        one(id);
        Map<String, Object> values = profile(body, false);
        if (values.isEmpty())
            throw ApiException.badRequest("Không có trường nào để cập nhật");
        if (values.containsKey("date_of_birth")
                && !LocalDate.parse(values.get("date_of_birth").toString()).isBefore(LocalDate.now(clock)))
            throw ApiException.badRequest("date_of_birth phải là ngày trong quá khứ");
        return db.rpc("update_member_profile", Map.of("p_id", id, "p_values", values)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hội viên"));
    }

    public Map<String, Object> status(long id, String status) {
        if (status == null || !Set.of("Active", "Inactive").contains(status))
            throw ApiException.badRequest("status chỉ nhận Active hoặc Inactive");
        one(id);
        db.update("member", "member_id", id, Map.of("status", status));
        db.update("account", "member_id", id, Map.of("status", status.equals("Active") ? "Active" : "Locked"));
        return one(id);
    }

    private Map<String, Object> one(long id) {
        return db.select("member", Map.of("member_id", "eq." + id), null, 1).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy hội viên"));
    }

    private Map<String, Object> profile(Map<String, Object> b, boolean creating) {
        Map<String, Object> v = new LinkedHashMap<>();
        if (creating || b.containsKey("fullName"))
            put(v, b, "fullName", "full_name", true, 100);
        if (creating || b.containsKey("gender"))
            put(v, b, "gender", "gender", true, 20);
        if (b.containsKey("dateOfBirth"))
            v.put("date_of_birth", Input.date(b, "dateOfBirth").toString());
        else if (creating)
            throw ApiException.badRequest("dateOfBirth là trường bắt buộc");
        if (b.containsKey("phone")) {
            String p = Input.text(b, "phone", true, 20);
            Input.phone(p);
            v.put("phone", p);
        } else if (creating)
            throw ApiException.badRequest("phone là trường bắt buộc");
        if (b.containsKey("email")) {
            String e = Input.text(b, "email", false, 100);
            Input.email(e, false);
            v.put("email", e);
        }
        put(v, b, "address", "address", false, 255);
        put(v, b, "trainingObjective", "training_objective", false, 255);
        put(v, b, "internalNote", "internal_note", false, 1000);
        if (creating)
            v.put("status", "Active");
        return v;
    }

    private void put(Map<String, Object> out, Map<String, Object> in, String from, String to, boolean required,
            int max) {
        if (in.containsKey(from))
            out.put(to, Input.text(in, from, required, max));
        else if (required)
            throw ApiException.badRequest(from + " là trường bắt buộc");
    }

    private String randomPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "A9!";
    }

}
