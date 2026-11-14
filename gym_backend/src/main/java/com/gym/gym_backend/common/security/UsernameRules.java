package com.gym.gym_backend.common.security;

import com.gym.gym_backend.common.exception.ApiException;

public final class UsernameRules {
    public static final String PATTERN = "[A-Za-z0-9_.@+() \\-]{1,50}";

    private UsernameRules() {
    }

    public static void validate(String username) {
        if (username == null || username.isBlank() || !username.matches(PATTERN)) {
            throw ApiException.badRequest("Tên đăng nhập chỉ được chứa chữ cái không dấu, số, dấu cách và các ký tự _ . @ + - ( )");
        }
    }
}
