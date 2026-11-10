package com.gym.gym_backend.auth;

import com.gym.gym_backend.auth.dto.BootstrapRequest;
import com.gym.gym_backend.auth.dto.ChangePasswordRequest;
import com.gym.gym_backend.auth.dto.LoginRequest;
import com.gym.gym_backend.auth.dto.LoginResponse;
import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.common.security.CurrentUser;
import com.gym.gym_backend.common.security.JwtService;
import com.gym.gym_backend.common.security.UserRole;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private SupabaseDataClient db;

    @Mock
    private PasswordEncoder passwordEncoder;

    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(JsonMapper.builder().build(), "test-secret-that-is-at-least-32-bytes", 600);
        authService = new AuthService(db, passwordEncoder, jwtService, "bootstrap-token", 600);
    }

    @Test
    void loginReturnsSignedTokenForActiveAccount() {
        Map<String, Object> account = account(7L, "staff.one", "Staff", "Active", false);
        when(db.select("account", Map.of("username", "eq.staff.one"), null, 1)).thenReturn(List.of(account));
        when(passwordEncoder.matches("password123", "bcrypt-hash")).thenReturn(true);

        LoginResponse result = authService.login(new LoginRequest("staff.one", "password123"));

        assertEquals("Bearer", result.tokenType());
        assertEquals(UserRole.Staff, result.user().role());
        assertEquals(7L, jwtService.verify(result.accessToken()).accountId());
    }

    @Test
    void loginRejectsInvalidPasswordAndLockedAccounts() {
        when(db.select("account", Map.of("username", "eq.staff.one"), null, 1))
                .thenReturn(List.of(account(7L, "staff.one", "Staff", "Active", false)));
        when(passwordEncoder.matches("bad-password", "bcrypt-hash")).thenReturn(false);
        assertEquals(401, assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("staff.one", "bad-password"))).status().value());

        when(db.select("account", Map.of("username", "eq.locked"), null, 1))
                .thenReturn(List.of(account(8L, "locked", "Staff", "Locked", false)));
        when(passwordEncoder.matches("password123", "bcrypt-hash")).thenReturn(true);
        assertEquals(401, assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("locked", "password123"))).status().value());
    }

    @Test
    void bootstrapCreatesTheFirstAdminAndRejectsAnyLaterAttempt() {
        when(db.select("account", Map.of(), null, 1)).thenReturn(List.of());
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(db.insert(eq("account"), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> values = new HashMap<>(invocation.getArgument(1));
            values.put("account_id", 1L);
            return List.of(values);
        });

        LoginResponse result = authService.bootstrap("bootstrap-token",
                new BootstrapRequest("owner", "password123", "Gym Admin"));

        assertEquals(UserRole.Admin, result.user().role());
        assertFalse(result.user().mustChangePassword());
        assertEquals(1L, jwtService.verify(result.accessToken()).accountId());
        verify(db).insert(eq("account"), org.mockito.ArgumentMatchers.argThat(values ->
                "Admin".equals(values.get("role")) && Boolean.FALSE.equals(values.get("must_change_password"))));

        when(db.select("account", Map.of(), null, 1)).thenReturn(List.of(account(1L, "owner", "Admin", "Active", false)));
        assertEquals(409, assertThrows(ApiException.class,
                () -> authService.bootstrap("bootstrap-token",
                        new BootstrapRequest("another", "password123", "Another Admin")))
                .status().value());
    }

    @Test
    void bootstrapRequiresConfiguredTokenAndMatchingValue() {
        AuthService withoutToken = new AuthService(db, passwordEncoder, jwtService, "", 600);
        assertEquals(401, assertThrows(ApiException.class,
                () -> withoutToken.bootstrap("anything", new BootstrapRequest("owner", "password123", "Admin")))
                .status().value());
        assertEquals(401, assertThrows(ApiException.class,
                () -> authService.bootstrap("wrong-token", new BootstrapRequest("owner", "password123", "Admin")))
                .status().value());
        verify(db, never()).insert(eq("account"), anyMap());
    }

    @Test
    void passwordChangeChecksConfirmationAndReturnsUpdatedToken() {
        CurrentUser current = new CurrentUser(7L, "staff.one", "Staff One", UserRole.Staff, null, null, true);
        when(db.select("account", Map.of("account_id", "eq.7"), null, 1))
                .thenReturn(List.of(account(7L, "staff.one", "Staff", "Active", true)));
        when(passwordEncoder.matches("temporary123", "bcrypt-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-bcrypt-hash");
        Map<String, Object> updated = account(7L, "staff.one", "Staff", "Active", false);
        updated.put("password_hash", "new-bcrypt-hash");
        when(db.update(eq("account"), eq("account_id"), eq(7L), anyMap())).thenReturn(List.of(updated));

        assertThrows(ApiException.class, () -> authService.changePassword(current,
                new ChangePasswordRequest("temporary123", "new-password", "different-password")));
        verify(db, never()).update(eq("account"), eq("account_id"), eq(7L), anyMap());

        LoginResponse response = authService.changePassword(current,
                new ChangePasswordRequest("temporary123", "new-password", "new-password"));
        assertFalse(response.user().mustChangePassword());
        assertTrue(jwtService.verify(response.accessToken()) != null);
        verify(passwordEncoder).encode("new-password");
    }

    private static Map<String, Object> account(long id, String username, String role, String status,
            boolean mustChangePassword) {
        Map<String, Object> row = new HashMap<>();
        row.put("account_id", id);
        row.put("username", username);
        row.put("full_name", username);
        row.put("password_hash", "bcrypt-hash");
        row.put("role", role);
        row.put("status", status);
        row.put("must_change_password", mustChangePassword);
        row.put("member_id", null);
        row.put("trainer_id", null);
        return row;
    }
}
