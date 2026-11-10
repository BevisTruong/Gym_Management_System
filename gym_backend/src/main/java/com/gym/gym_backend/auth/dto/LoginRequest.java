package com.gym.gym_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_.@+-]{1,50}")String username,@NotBlank String password){}
