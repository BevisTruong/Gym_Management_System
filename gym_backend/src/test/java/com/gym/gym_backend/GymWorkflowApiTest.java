package com.gym.gym_backend;

import com.gym.gym_backend.common.security.CurrentUser;
import com.gym.gym_backend.common.security.JwtService;
import com.gym.gym_backend.common.security.UserRole;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "supabase.url=http://127.0.0.1:54321",
        "supabase.secret-key=test-key",
        "app.security.jwt-secret=test-secret-that-is-at-least-32-bytes",
        "app.security.bootstrap-token=test-bootstrap-token",
        "app.simulated-now=2026-05-10T03:00:00Z"
})
@AutoConfigureMockMvc
class GymWorkflowApiTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private SupabaseDataClient db;

    private final Map<String, Object> savedMember = new HashMap<>();
    private final Map<String, Object> savedAccount = new HashMap<>();
    private final Map<String, Object> savedMembership = new HashMap<>();
    private final Map<String, Object> savedSession = new HashMap<>();

    @BeforeEach
    void setUp() {
        savedMember.clear();
        savedAccount.clear();
        savedMembership.clear();
        savedSession.clear();

        when(db.select("member", Map.of("full_name", "ilike.Nguyen Van An", "date_of_birth", "eq.1995-02-11"),
                null, 5)).thenReturn(List.of());
        when(db.insert(eq("member"), anyMap())).thenAnswer(call -> {
            savedMember.putAll(call.getArgument(1, Map.class));
            savedMember.put("member_id", 31L);
            return List.of(new HashMap<>(savedMember));
        });
        when(db.insert(eq("account"), anyMap())).thenAnswer(call -> {
            savedAccount.putAll(call.getArgument(1, Map.class));
            savedAccount.put("account_id", 8L);
            savedAccount.put("member_id", 31L);
            return List.of(new HashMap<>(savedAccount));
        });
        when(db.select("account", Map.of("username", "eq.0901234567"), null, 1))
                .thenAnswer(call -> List.of(new HashMap<>(savedAccount)));
        when(db.select("account", Map.of("account_id", "eq.8"), null, 1))
                .thenAnswer(call -> List.of(new HashMap<>(savedAccount)));

        when(db.select("member", Map.of("member_id", "eq.31"), null, 1))
                .thenAnswer(call -> List.of(new HashMap<>(savedMember)));
        when(db.select("membership_plan", Map.of("plan_id", "eq.2"), null, 1))
                .thenReturn(List.of(Map.of("plan_id", 2, "status", "Available", "duration_months", 1,
                        "price", 450000)));
        when(db.select(eq("membership"), anyMap(), eq("end_date.desc"), eq(200))).thenReturn(List.of());
        when(db.select(eq("membership"), anyMap(), eq(null), eq(1))).thenAnswer(call -> {
            Map<String, String> filters = call.getArgument(1);
            return "lte.2026-05-13".equals(filters.get("start_date"))
                    ? List.of(new HashMap<>(savedMembership)) : List.of();
        });
        when(db.insert(eq("membership"), anyMap())).thenAnswer(call -> {
            savedMembership.putAll(call.getArgument(1, Map.class));
            savedMembership.put("membership_id", 52L);
            return List.of(new HashMap<>(savedMembership));
        });

        when(db.select("trainer", Map.of("trainer_id", "eq.5"), null, 1))
                .thenReturn(List.of(Map.of("trainer_id", 5, "status", "Active")));
        when(db.select(eq("training_session"), anyMap(), eq(null), eq(1))).thenReturn(List.of());
        when(db.insert(eq("training_session"), anyMap())).thenAnswer(call -> {
            savedSession.putAll(call.getArgument(1, Map.class));
            savedSession.put("session_id", 70L);
            savedSession.put("status", "Scheduled");
            return List.of(new HashMap<>(savedSession));
        });

        when(db.update(eq("account"), eq("account_id"), eq(8L), anyMap())).thenAnswer(call -> {
            savedAccount.putAll(call.getArgument(3, Map.class));
            return List.of(new HashMap<>(savedAccount));
        });
        when(db.select(eq("member"), eq(Map.of("member_id", "eq.31")), eq(null), eq(1),
                eq("member_id,full_name,gender,date_of_birth,phone,email,address,training_objective,status")))
                .thenAnswer(call -> List.of(new HashMap<>(savedMember)));
        when(db.select(eq("membership"), eq(Map.of("member_id", "eq.31")), eq("start_date.desc"), eq(200),
                eq("*,membership_plan(name,duration_months,benefits)")))
                .thenAnswer(call -> List.of(new HashMap<>(savedMembership)));
        when(db.select(eq("training_session"), eq(Map.of("member_id", "eq.31")),
                eq("session_date.desc,start_time.desc"), eq(500), eq("*,trainer(full_name)")))
                .thenAnswer(call -> List.of(new HashMap<>(savedSession)));
    }

    @Test
    void memberCreationLoginPasswordChangeRegistrationSchedulingAndPersonalPortalWorkTogether() throws Exception {
        String staffToken = token(new CurrentUser(1, "staff", "Staff", UserRole.Staff, null, null, false));
        String memberBody = """
                {"fullName":"Nguyen Van An","gender":"Male","dateOfBirth":"1995-02-11",
                 "phone":"0901234567","email":"an@example.com","trainingObjective":"Strength"}
                """;
        MvcResult createdMember = mvc.perform(post("/api/members").header("Authorization", "Bearer " + staffToken)
                        .contentType("application/json").content(memberBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member_id").value(31))
                .andReturn();
        String temporaryPassword = JSON.readTree(createdMember.getResponse().getContentAsString())
                .path("data").path("temporaryPassword").asString();
        assertFalse(temporaryPassword.isBlank());

        MvcResult login = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"0901234567\",\"password\":\"" + temporaryPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(true))
                .andReturn();
        String firstLoginToken = JSON.readTree(login.getResponse().getContentAsString())
                .path("data").path("accessToken").asString();
        mvc.perform(get("/api/portal/member").header("Authorization", "Bearer " + firstLoginToken))
                .andExpect(status().isForbidden());

        MvcResult changed = mvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + firstLoginToken).contentType("application/json")
                        .content("{\"currentPassword\":\"" + temporaryPassword
                                + "\",\"newPassword\":\"MemberPass123\",\"confirmPassword\":\"MemberPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(false))
                .andReturn();
        String memberToken = JSON.readTree(changed.getResponse().getContentAsString())
                .path("data").path("accessToken").asString();
        assertNotNull(memberToken);

        mvc.perform(post("/api/memberships/register").header("Authorization", "Bearer " + staffToken)
                        .contentType("application/json")
                        .content("{\"memberId\":31,\"planId\":2,\"startDate\":\"2026-05-12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.end_date").value("2026-06-11"))
                .andExpect(jsonPath("$.data.membershipStatus").value("Upcoming"));

        mvc.perform(post("/api/sessions").header("Authorization", "Bearer " + staffToken)
                        .contentType("application/json")
                        .content("""
                                {"memberId":31,"trainerId":5,"sessionDate":"2026-05-13",
                                 "startTime":"10:00","endTime":"11:00","note":"Workout"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session_id").value(70))
                .andExpect(jsonPath("$.data.status").value("Scheduled"));

        mvc.perform(get("/api/portal/member").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.member_id").value(31))
                .andExpect(jsonPath("$.data.memberships[0].membershipStatus").value("Upcoming"))
                .andExpect(jsonPath("$.data.sessions[0].session_id").value(70))
                .andExpect(jsonPath("$.data.profile.internal_note").doesNotExist());
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
