package com.gym.gym_backend.account;

import com.gym.gym_backend.common.response.ApiResponse;
import com.gym.gym_backend.common.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService s) {
        service = s;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(role, status));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> b) {
        return ApiResponse.ok(service.create(b),
                "Tạo tài khoản thành công. Hãy cung cấp mật khẩu tạm thời cho nhân viên.");
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> status(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
            @RequestBody Map<String, String> b) {
        return ApiResponse.ok(service.status(user.accountId(), id, b.get("status")));
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Map<String, Object>> reset(@PathVariable long id) {
        return ApiResponse.ok(service.reset(id), "Đã đặt lại mật khẩu. Hãy cung cấp mật khẩu tạm thời cho người dùng.");
    }

}
