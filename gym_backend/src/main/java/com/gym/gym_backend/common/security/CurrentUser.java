package com.gym.gym_backend.common.security;public record CurrentUser(long accountId,String username,String fullName,UserRole role,Long memberId,Long trainerId,boolean mustChangePassword){}
