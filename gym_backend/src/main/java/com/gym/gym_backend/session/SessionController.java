package com.gym.gym_backend.session;

import com.gym.gym_backend.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/sessions")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class SessionController {
    private final SessionService service;

    public SessionController(SessionService s) {
        service = s;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String date,
            @RequestParam(required = false) String memberId, @RequestParam(required = false) String trainerId,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(service.list(date, memberId, trainerId, status, limit));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> b) {
        return ApiResponse.ok(service.create(b));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return ApiResponse.ok(service.update(id, b));
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable long id) {
        return ApiResponse.ok(service.cancel(id));
    }

    @PostMapping("/available-trainers")
    public ApiResponse<List<Map<String, Object>>> available(@RequestBody Map<String, Object> b) {
        return ApiResponse.ok(service.availableTrainers(b));
    }

}
