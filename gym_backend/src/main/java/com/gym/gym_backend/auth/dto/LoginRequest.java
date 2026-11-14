package com.gym.gym_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import com.gym.gym_backend.common.security.UsernameRules;

public record LoginRequest(@NotBlank @Pattern(regexp=UsernameRules.PATTERN) String username,
        @NotBlank String password) {}
