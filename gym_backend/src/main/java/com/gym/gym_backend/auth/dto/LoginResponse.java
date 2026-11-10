package com.gym.gym_backend.auth.dto;

import com.gym.gym_backend.common.security.CurrentUser;

public record LoginResponse(String accessToken,String tokenType,long expiresIn,CurrentUser user){}
