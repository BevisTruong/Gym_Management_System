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
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

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
        String token = jwtService.issue(new CurrentUser(8, "member", "Member", UserRole.Member, 31L, null, false));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffAndTrainerCannotUseAdminOnlyOrStaffOnlyApis() throws Exception {
        String staff = jwtService.issue(new CurrentUser(9, "staff", "Staff", UserRole.Staff, null, null, false));
        String trainer = jwtService.issue(new CurrentUser(10, "trainer", "Trainer", UserRole.Trainer, null, 5L,
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
}
