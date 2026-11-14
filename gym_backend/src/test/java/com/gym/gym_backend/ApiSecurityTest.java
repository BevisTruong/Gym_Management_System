package com.gym.gym_backend;

import com.gym.gym_backend.common.security.CurrentUser;
import com.gym.gym_backend.common.security.JwtService;
import com.gym.gym_backend.common.security.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest(properties = {
        "supabase.url=http://127.0.0.1:54321",
        "supabase.secret-key=test-key",
        "app.security.jwt-secret=test-secret-that-is-at-least-32-bytes",
        "app.security.bootstrap-token=test-bootstrap-token"
})
@AutoConfigureMockMvc
class ApiSecurityTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient db;
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private jakarta.validation.Validator validator;

    @Test
    void lockedAccountAndChangedPasswordInvalidateExistingToken() throws Exception {
        String token = token(new CurrentUser(8, "member", "Member", UserRole.Member, 31L, null, false));
        org.mockito.Mockito.when(db.select("account", java.util.Map.of("account_id", "eq.8"), null, 1))
                .thenReturn(java.util.List.of(java.util.Map.of("status", "Locked", "password_hash", "test-hash")))
                .thenReturn(java.util.List.of(java.util.Map.of("status", "Active", "password_hash", "new-hash")));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void formattedPhoneCanBeUsedAsUsername() {
        var request = new com.gym.gym_backend.auth.dto.LoginRequest("090 123 4567", "password");
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void browserPreflightIsAllowedForTheConfiguredFrontendOrigin() throws Exception {
        mvc.perform(options("/api/members")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void protectedEndpointRejectsMissingOrInvalidToken() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedMemberCanReadIdentityButCannotManageMembers() throws Exception {
        String token = token(new CurrentUser(8, "member", "Member", UserRole.Member, 31L, null, false));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffAndTrainerCannotUseAdminOnlyOrStaffOnlyApis() throws Exception {
        String staff = token(new CurrentUser(9, "staff", "Staff", UserRole.Staff, null, null, false));
        String trainer = token(new CurrentUser(10, "trainer", "Trainer", UserRole.Trainer, null, 5L,
                false));

        mvc.perform(post("/api/plans").header("Authorization", "Bearer " + staff).contentType("application/json")
                .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + staff))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/trainers").header("Authorization", "Bearer " + trainer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/memberships/member/31").header("Authorization", "Bearer " + trainer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/memberships").header("Authorization", "Bearer " + trainer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/sessions").header("Authorization", "Bearer " + trainer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/expirations").header("Authorization", "Bearer " + trainer))
                .andExpect(status().isForbidden());
    }

    private String token(CurrentUser user) {
        var row = new java.util.HashMap<String, Object>();
        row.put("account_id", user.accountId());
        row.put("username", user.username());
        row.put("full_name", user.fullName());
        row.put("role", user.role().name());
        row.put("member_id", user.memberId());
        row.put("trainer_id", user.trainerId());
        row.put("must_change_password", user.mustChangePassword());
        row.put("status", "Active");
        row.put("password_hash", "test-hash");
        org.mockito.Mockito.when(db.select("account", java.util.Map.of("account_id", "eq." + user.accountId()), null, 1))
                .thenReturn(java.util.List.of(row));
        return jwtService.issue(user, "test-hash");
    }
}
