package com.gym.gym_backend.portal;

import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.common.response.ApiResponse;
import com.gym.gym_backend.common.security.CurrentUser;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import com.gym.gym_backend.membership.MembershipService;
import com.gym.gym_backend.session.SessionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/portal")
public class PortalController {
    private final SessionService sessions;
    private final MembershipService memberships;
    private final SupabaseDataClient db;

    public PortalController(SessionService s, MembershipService m, SupabaseDataClient db) {
        sessions = s;
        memberships = m;
        this.db = db;
    }

    @GetMapping("/trainer/schedule")
    @PreAuthorize("hasRole('TRAINER')")
    public ApiResponse<List<Map<String, Object>>> trainer(@AuthenticationPrincipal CurrentUser user) {
        if (user.trainerId() == null)
            throw ApiException.unauthorized("Tài khoản chưa liên kết hồ sơ huấn luyện viên");
        return ApiResponse.ok(sessions.trainerSchedule(user.trainerId()));
    }

    @GetMapping("/member")
    @PreAuthorize("hasRole('MEMBER')")
    public ApiResponse<Map<String, Object>> member(@AuthenticationPrincipal CurrentUser user) {
        if (user.memberId() == null)
            throw ApiException.unauthorized("Tài khoản chưa liên kết hồ sơ hội viên");
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> profile = db
                .select("member", Map.of("member_id", "eq." + user.memberId()), null, 1,
                        "member_id,full_name,gender,date_of_birth,phone,email,address,training_objective,status")
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Không tìm thấy hồ sơ hội viên"));
        data.put("profile", profile);
        data.put("memberships", memberships.forMember(user.memberId()));
        data.put("sessions", sessions.memberSchedule(user.memberId()));
        return ApiResponse.ok(data);
    }

}
