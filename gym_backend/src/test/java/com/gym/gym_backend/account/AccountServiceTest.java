package com.gym.gym_backend.account;

import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock
    private SupabaseDataClient db;

    @Mock
    private PasswordEncoder encoder;

    private AccountService service() {
        return new AccountService(db, encoder);
    }

    @Test
    void createAdminOrStaffStoresHashAndReturnsTemporaryPasswordOnlyOnce() {
        when(encoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("salted-hash");
        when(db.insert(eq("account"), anyMap())).thenAnswer(call -> {
            Map<String, Object> row = new HashMap<>(call.getArgument(1, Map.class));
            row.put("account_id", 12L);
            return List.of(row);
        });

        Map<String, Object> result = service().create(Map.of("username", "staff1", "fullName", "Staff One",
                "role", "Staff"));

        assertEquals(12L, result.get("account_id"));
        assertFalse(result.containsKey("password_hash"));
        assertNotNull(result.get("temporaryPassword"));
        verify(db).insert(eq("account"), org.mockito.ArgumentMatchers.argThat(values ->
                "salted-hash".equals(values.get("password_hash")) && Boolean.TRUE.equals(values.get("must_change_password"))));
    }

    @Test
    void createRejectsMemberAndTrainerRolesBecauseTheyMustBeCreatedWithProfiles() {
        assertEquals(400, assertThrows(ApiException.class, () -> service().create(Map.of("username", "x",
                "fullName", "X", "role", "Member"))).status().value());
        verify(db, never()).insert(eq("account"), anyMap());
    }

    @Test
    void lastActiveAdminAndCurrentAdminCannotBeLocked() {
        when(db.select("account", Map.of("account_id", "eq.1"), null, 1))
                .thenReturn(List.of(account(1, "Admin", "Active")));
        when(db.select("account", Map.of("role", "eq.Admin", "status", "eq.Active"), null, 2))
                .thenReturn(List.of(account(1, "Admin", "Active")));

        assertEquals(409, assertThrows(ApiException.class, () -> service().status(2, 1, "Locked")).status().value());
        assertEquals(409, assertThrows(ApiException.class, () -> service().status(1, 1, "Locked")).status().value());
        verify(db, never()).update(eq("account"), eq("account_id"), eq(1L), anyMap());
    }

    @Test
    void resetPasswordSetsHashAndRequiresPasswordChange() {
        when(db.select("account", Map.of("account_id", "eq.3"), null, 1))
                .thenReturn(List.of(account(3, "Staff", "Active")));
        when(encoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("new-hash");
        when(db.update(eq("account"), eq("account_id"), eq(3L), anyMap())).thenAnswer(call -> {
            Map<String, Object> row = new HashMap<>(account(3, "Staff", "Active"));
            row.putAll(call.getArgument(3, Map.class));
            return List.of(row);
        });

        Map<String, Object> result = service().reset(3);

        assertFalse(result.containsKey("password_hash"));
        assertNotNull(result.get("temporaryPassword"));
        verify(db).update(eq("account"), eq("account_id"), eq(3L),
                org.mockito.ArgumentMatchers.argThat(values ->
                        "new-hash".equals(values.get("password_hash"))
                                && Boolean.TRUE.equals(values.get("must_change_password"))));
    }

    @Test
    void accountListingNeverReturnsPasswordHashes() {
        when(db.select("account", Map.of(), "account_id.asc", 300))
                .thenReturn(List.of(account(1, "Admin", "Active")));

        List<Map<String, Object>> accounts = service().list(null, null);

        assertEquals(1, accounts.size());
        assertFalse(accounts.getFirst().containsKey("password_hash"));
    }

    private static Map<String, Object> account(long id, String role, String status) {
        return new HashMap<>(Map.of("account_id", id, "username", "user" + id, "role", role,
                "status", status, "password_hash", "stored-hash"));
    }
}
