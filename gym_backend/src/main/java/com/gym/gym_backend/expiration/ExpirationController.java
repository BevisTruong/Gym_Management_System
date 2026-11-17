package com.gym.gym_backend.expiration;

import com.gym.gym_backend.common.response.ApiResponse;
import com.gym.gym_backend.membership.MembershipService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/expirations")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class ExpirationController {
    private final MembershipService service;

    public ExpirationController(MembershipService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.expiring());
    }

}
