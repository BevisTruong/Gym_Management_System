package com.gym.gym_backend.portal;

import com.gym.gym_backend.common.security.CurrentUser;
import com.gym.gym_backend.common.security.JwtService;
import com.gym.gym_backend.common.security.UserRole;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "supabase.url=http://127.0.0.1:54321",
        "supabase.secret-key=test-key",
        "app.security.jwt-secret=test-secret-that-is-at-least-32-bytes"
})
@AutoConfigureMockMvc
class PortalAccessApiTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private SupabaseDataClient db;

    @Test
    void memberPortalReturnsOnlyTheProfileBoundToTheAuthenticatedAccount() throws Exception {
        when(db.select(eq("member"), eq(Map.of("member_id", "eq.31")), eq(null), eq(1),
                eq("member_id,full_name,gender,date_of_birth,phone,email,address,training_objective,status")))
                .thenReturn(List.of(Map.of("member_id", 31, "full_name", "Member One", "status", "Active")));
        when(db.select(eq("membership"), eq(Map.of("member_id", "eq.31")), eq("start_date.desc"), eq(200),
                eq("*,membership_plan(name,duration_months,benefits)"))).thenReturn(List.of());
        when(db.select(eq("training_session"), eq(Map.of("member_id", "eq.31")),
                eq("session_date.desc,start_time.desc"), eq(500), eq("*,trainer(full_name)"))).thenReturn(List.of());
        String token = token(new CurrentUser(8, "member", "Member One", UserRole.Member, 31L, null, false));

        mvc.perform(get("/api/portal/member").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.member_id").value(31))
                .andExpect(jsonPath("$.data.memberships").isArray())
                .andExpect(jsonPath("$.data.sessions").isArray())
                .andExpect(jsonPath("$.data.profile.internal_note").doesNotExist());

        verify(db).select(eq("member"), eq(Map.of("member_id", "eq.31")), eq(null), eq(1),
                eq("member_id,full_name,gender,date_of_birth,phone,email,address,training_objective,status"));
    }

    @Test
    void trainerPortalUsesTrainerIdFromTheToken() throws Exception {
        when(db.select(eq("training_session"), eq(Map.of("trainer_id", "eq.5")),
                eq("session_date.asc,start_time.asc"), eq(500), eq("*,member(full_name,training_objective)")))
                .thenReturn(List.of(Map.of("session_id", 70, "status", "Scheduled", "session_date", "2026-12-01",
                        "start_time", "09:00", "end_time", "10:00", "member", Map.of("full_name", "Client"))));
        String token = token(new CurrentUser(9, "trainer", "Trainer Five", UserRole.Trainer, null, 5L,
                false));

        mvc.perform(get("/api/portal/trainer/schedule").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].session_id").value(70))
                .andExpect(jsonPath("$.data[0].member.full_name").value("Client"));

        verify(db).select(eq("training_session"), eq(Map.of("trainer_id", "eq.5")),
                eq("session_date.asc,start_time.asc"), eq(500), eq("*,member(full_name,training_objective)"));
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
