package com.gym.gym_backend.auth;

import com.gym.gym_backend.auth.dto.BootstrapRequest;
import com.gym.gym_backend.auth.dto.ChangePasswordRequest;
import com.gym.gym_backend.auth.dto.LoginRequest;
import com.gym.gym_backend.auth.dto.LoginResponse;
import com.gym.gym_backend.common.response.ApiResponse;
import com.gym.gym_backend.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest body) {
        return ApiResponse.ok(service.login(body));
    }

    @PostMapping("/bootstrap")
    public ApiResponse<LoginResponse> bootstrap(
            @RequestHeader(value = "X-Bootstrap-Token", required = false) String token,
            @Valid @RequestBody BootstrapRequest body) {
        return ApiResponse.ok(service.bootstrap(token, body));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> me(@AuthenticationPrincipal CurrentUser user) {
        return ApiResponse.ok(user);
    }

    @PostMapping("/change-password")
    public ApiResponse<LoginResponse> changePassword(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody ChangePasswordRequest body) {
        return ApiResponse.ok(service.changePassword(user, body), "Đổi mật khẩu thành công.");
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

}
