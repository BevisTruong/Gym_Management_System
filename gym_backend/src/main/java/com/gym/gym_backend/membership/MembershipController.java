package com.gym.gym_backend.membership;

import com.gym.gym_backend.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/memberships")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class MembershipController {
    private final MembershipService service;

    public MembershipController(MembershipService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(defaultValue = "500") int limit) {
        return ApiResponse.ok(service.list(limit));
    }

    @GetMapping("/member/{memberId}")
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable long memberId) {
        return ApiResponse.ok(service.forMember(memberId));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> b) {
        return ApiResponse.ok(service.register(b, false));
    }

    @PostMapping("/renew")
    public ApiResponse<Map<String, Object>> renew(@RequestBody Map<String, Object> b) {
        return ApiResponse.ok(service.register(b, true));
    }

}
