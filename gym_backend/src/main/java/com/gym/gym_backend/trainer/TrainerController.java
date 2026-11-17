package com.gym.gym_backend.trainer;

import com.gym.gym_backend.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/trainers")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class TrainerController {
    private final TrainerService service;

    public TrainerController(TrainerService s) {
        service = s;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String search,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.list(search, status, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> b) {
        return ApiResponse.ok(service.create(b),
                "Tạo huấn luyện viên thành công. Hãy cung cấp mật khẩu tạm thời cho huấn luyện viên.");
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return ApiResponse.ok(service.update(id, b));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable long id, @RequestBody Map<String, String> b) {
        return ApiResponse.ok(service.status(id, b.get("status")));
    }

}
