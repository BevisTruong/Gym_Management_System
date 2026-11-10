package com.gym.gym_backend.auth;

import com.gym.gym_backend.auth.dto.BootstrapRequest;
import com.gym.gym_backend.auth.dto.ChangePasswordRequest;
import com.gym.gym_backend.auth.dto.LoginRequest;
import com.gym.gym_backend.auth.dto.LoginResponse;
import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.common.security.CurrentUser;
import com.gym.gym_backend.common.security.JwtService;
import com.gym.gym_backend.common.security.UserRole;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {
    private final SupabaseDataClient db;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final String bootstrapToken;
    private final long tokenLifetime;

    public AuthService(SupabaseDataClient db, PasswordEncoder passwords, JwtService jwt,
            @Value("${app.security.bootstrap-token:}") String bootstrapToken,
            @Value("${app.security.jwt-expiration-seconds:28800}") long tokenLifetime) {
        this.db = db;
        this.passwords = passwords;
        this.jwt = jwt;
        this.bootstrapToken = bootstrapToken;
        this.tokenLifetime = tokenLifetime;
    }

    public LoginResponse login(LoginRequest request) {
        List<Map<String, Object>> accounts = db.select("account", Map.of("username", "eq." + request.username()), null,
                1);
        if (accounts.isEmpty()
                || !passwords.matches(request.password(), String.valueOf(accounts.getFirst().get("password_hash"))))
            throw ApiException.unauthorized("Tên đăng nhập hoặc mật khẩu không chính xác");
        Map<String, Object> account = accounts.getFirst();
        if (!"Active".equals(account.get("status")))
            throw ApiException.unauthorized("Tài khoản đã bị khóa");
        CurrentUser user = currentUser(account);
        return new LoginResponse(jwt.issue(user), "Bearer", tokenLifetime, user);
    }

    public LoginResponse bootstrap(String token, BootstrapRequest request) {
        if (bootstrapToken.isBlank() || token == null || !java.security.MessageDigest
                .isEqual(bootstrapToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw ApiException.unauthorized("Thông tin khởi tạo quản trị viên không hợp lệ");
        if (!db.select("account", Map.of(), null, 1).isEmpty())
            throw ApiException.conflict("Hệ thống đã có tài khoản; thao tác khởi tạo đã bị khóa");
        Map<String, Object> newAccount = new HashMap<>();
        newAccount.put("username", request.username());
        newAccount.put("full_name", request.fullName().trim());
        newAccount.put("password_hash", passwords.encode(request.password()));
        newAccount.put("role", "Admin");
        newAccount.put("status", "Active");
        newAccount.put("must_change_password", false);
        Map<String, Object> created = db.insert("account", newAccount).getFirst();
        CurrentUser user = currentUser(created);
        return new LoginResponse(jwt.issue(user), "Bearer", tokenLifetime, user);
    }

    public LoginResponse changePassword(CurrentUser user, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword()))
            throw ApiException.badRequest("Mật khẩu mới nhập lại không khớp");
        Map<String, Object> account = byId(user.accountId());
        if (!passwords.matches(request.currentPassword(), String.valueOf(account.get("password_hash"))))
            throw ApiException.badRequest("Mật khẩu hiện tại không chính xác");
        Map<String, Object> update = Map.of("password_hash", passwords.encode(request.newPassword()),
                "must_change_password", false);
        CurrentUser updated = currentUser(db.update("account", "account_id", user.accountId(), update).getFirst());
        return new LoginResponse(jwt.issue(updated), "Bearer", tokenLifetime, updated);
    }

    public Map<String, Object> byId(long id) {
        return db.select("account", Map.of("account_id", "eq." + id), null, 1).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
    }

    public CurrentUser currentUser(Map<String, Object> row) {
        return new CurrentUser(((Number) row.get("account_id")).longValue(), String.valueOf(row.get("username")),
                String.valueOf(row.get("full_name")), UserRole.valueOf(String.valueOf(row.get("role"))),
                row.get("member_id") == null ? null : ((Number) row.get("member_id")).longValue(),
                row.get("trainer_id") == null ? null : ((Number) row.get("trainer_id")).longValue(),
                Boolean.TRUE.equals(row.get("must_change_password")));
    }

}
