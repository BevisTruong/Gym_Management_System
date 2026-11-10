package com.gym.gym_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BootstrapRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_.@+-]{1,50}")String username,@NotBlank @Size(min=8,max=72)String password,@NotBlank @Size(max=100)String fullName){}
