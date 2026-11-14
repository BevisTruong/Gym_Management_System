package com.gym.gym_backend;

import com.gym.gym_backend.common.security.CurrentUser;
import com.gym.gym_backend.common.security.JwtService;
import com.gym.gym_backend.common.security.UserRole;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opt-in read-only smoke test against the Supabase configured in gym_backend/.env. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "gym.real-supabase", matches = "true")
class SupabaseAccountReadSmokeTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SupabaseDataClient supabase;

    @Test
    void backendReadsAdminAccountsFromConfiguredSupabase() throws Exception {
        var admins = supabase.select("account", java.util.Map.of("role", "eq.Admin", "status", "eq.Active"), "account_id.asc", 1);
        assertFalse(admins.isEmpty());
        var admin = admins.getFirst();
        var user = new com.gym.gym_backend.auth.AuthService(supabase, null, jwtService, "", 1).currentUser(admin);
        String token = jwtService.issue(user, String.valueOf(admin.get("password_hash")));

        MvcResult result = mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = JSON.readTree(result.getResponse().getContentAsString());
        assertTrue(response.path("success").asBoolean());
        JsonNode accounts = response.path("data");
        assertTrue(accounts.isArray());
        assertFalse(accounts.isEmpty(), "The Supabase account table should contain the Admin account.");

        boolean hasActiveAdmin = false;
        for (JsonNode account : accounts) {
            assertFalse(account.has("password_hash"), "Account API must never return password hashes.");
            if ("Admin".equals(account.path("role").asString())
                    && "Active".equals(account.path("status").asString())) {
                hasActiveAdmin = true;
            }
        }
        assertTrue(hasActiveAdmin, "Expected at least one Active Admin account from Supabase.");
    }

    @Test
    void realAdminCredentialsAuthenticateAndTemporaryPasswordGuardIsApplied() throws Exception {
        String password = System.getenv("GYM_SMOKE_ADMIN_PASSWORD");
        Assumptions.assumeTrue(password != null && !password.isBlank(),
                "Set GYM_SMOKE_ADMIN_PASSWORD in the local process environment to run this check.");

        var admins = supabase.select("account",
                java.util.Map.of("role", "eq.Admin", "status", "eq.Active"), "account_id.asc", 1);
        assertFalse(admins.isEmpty(), "Expected an Active Admin account in Supabase.");
        String username = String.valueOf(admins.getFirst().get("username"));

        MvcResult login = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(JSON.writeValueAsString(java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode loginResponse = JSON.readTree(login.getResponse().getContentAsString());
        assertTrue(loginResponse.path("success").asBoolean());
        assertTrue("Admin".equals(loginResponse.path("data").path("user").path("role").asString()));

        String token = loginResponse.path("data").path("accessToken").asString();
        boolean mustChangePassword = loginResponse.path("data").path("user").path("mustChangePassword").asBoolean();
        assertFalse(mustChangePassword, "Admin must have completed the required first password change.");
        assertAdminEndpointsAreReachable(token);
    }

    private void assertAdminEndpointsAreReachable(String token) throws Exception {
        var auth = "Bearer " + token;
        mvc.perform(get("/api/auth/me").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/accounts").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/members").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/plans").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/trainers").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/memberships/member/0").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/memberships").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/expirations").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get("/api/sessions").header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(post("/api/sessions/available-trainers").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"sessionDate\":\"2099-01-01\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/portal/member").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/portal/trainer/schedule").header("Authorization", auth))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/accounts").header("Authorization", auth).contentType("application/json")
                        .content("{\"username\":\"x\",\"fullName\":\"X\",\"role\":\"Member\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/members").header("Authorization", auth).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/plans").header("Authorization", auth).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trainers").header("Authorization", auth).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/memberships/register").header("Authorization", auth)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/memberships/renew").header("Authorization", auth)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/sessions").header("Authorization", auth).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/members/0").header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(put("/api/members/0").header("Authorization", auth).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/plans/0").header("Authorization", auth).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/trainers/0").header("Authorization", auth).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/sessions/0").header("Authorization", auth).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/sessions/0/cancel").header("Authorization", auth))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/logout").header("Authorization", auth)).andExpect(status().isNoContent());
    }
}
