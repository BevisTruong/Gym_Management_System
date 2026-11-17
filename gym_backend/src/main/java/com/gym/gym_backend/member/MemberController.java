package com.gym.gym_backend.member;

import com.gym.gym_backend.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/members")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class MemberController {
    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
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
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(body),
                "Tạo hội viên thành công. Hãy cung cấp mật khẩu tạm thời cho hội viên.");
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.update(id, body));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(service.status(id, body.get("status")));
    }

}
