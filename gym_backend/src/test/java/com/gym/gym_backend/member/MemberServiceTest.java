package com.gym.gym_backend.member;

import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
class MemberServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-10T03:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private SupabaseDataClient db;

    @Mock
    private PasswordEncoder encoder;

    private MemberService service;

    @BeforeEach
    void setUp() {
        service = new MemberService(db, encoder, CLOCK);
    }

    @Test
    void createValidMemberCreatesLinkedTemporaryAccount() {
        when(db.select(eq("member"), anyMap(), eq(null), eq(5))).thenReturn(List.of());
        Map<String, Object> member = memberRow(41L);
        when(db.insert(eq("member"), anyMap())).thenReturn(List.of(member));
        when(encoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("hashed-temp-password");
        when(db.insert(eq("account"), anyMap())).thenAnswer(invocation ->
                List.of(invocation.getArgument(1, Map.class)));

        Map<String, Object> result = service.create(validMember());

        assertEquals(41L, result.get("member_id"));
        assertNotNull(result.get("temporaryPassword"));
        assertFalse(result.get("temporaryPassword").toString().isBlank());
        ArgumentCaptor<Map<String, Object>> account = mapCaptor();
        verify(db).insert(eq("account"), account.capture());
        assertEquals("Member", account.getValue().get("role"));
        assertEquals(41L, account.getValue().get("member_id"));
        assertEquals("0901234567", account.getValue().get("username"));
        assertEquals("hashed-temp-password", account.getValue().get("password_hash"));
        assertEquals(true, account.getValue().get("must_change_password"));
    }

    @Test
    void createRejectsInvalidPhoneAndNonPastBirthDate() {
        Map<String, Object> invalidPhone = validMember();
        invalidPhone.put("phone", "not-a-phone");
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(invalidPhone)).status().value());

        Map<String, Object> birthToday = validMember();
        birthToday.put("dateOfBirth", "2026-05-10");
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(birthToday)).status().value());
        verify(db, never()).insert(eq("member"), anyMap());
    }

    @Test
    void possibleDuplicateRequiresExplicitConfirmation() {
        when(db.select(eq("member"), anyMap(), eq(null), eq(5)))
                .thenReturn(List.of(memberRow(11L)));

        ApiException exception = assertThrows(ApiException.class, () -> service.create(validMember()));

        assertEquals(409, exception.status().value());
        verify(db, never()).insert(eq("member"), anyMap());
    }

    @Test
    void accountFailureCompensatesByDeletingNewMember() {
        when(db.select(eq("member"), anyMap(), eq(null), eq(5))).thenReturn(List.of());
        Map<String, Object> member = memberRow(41L);
        when(db.insert(eq("member"), anyMap())).thenReturn(List.of(member));
        when(encoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("hash");
        when(db.insert(eq("account"), anyMap())).thenThrow(new RuntimeException("account insert failed"));

        assertThrows(RuntimeException.class, () -> service.create(validMember()));

        verify(db).delete("member", "member_id", 41L);
    }

    @Test
    void statusChangeLocksAndUnlocksLinkedAccount() {
        Map<String, Object> member = memberRow(41L);
        when(db.select("member", Map.of("member_id", "eq.41"), null, 1)).thenReturn(List.of(member));
        when(db.update(eq("member"), eq("member_id"), eq(41L), anyMap())).thenReturn(List.of(member));
        when(db.update(eq("account"), eq("member_id"), eq(41L), anyMap())).thenReturn(List.of());

        service.status(41L, "Inactive");
        service.status(41L, "Active");

        verify(db).update("account", "member_id", 41L, Map.of("status", "Locked"));
        verify(db).update("account", "member_id", 41L, Map.of("status", "Active"));
        assertEquals(400, assertThrows(ApiException.class, () -> service.status(41L, "Deleted")).status().value());
    }

    @Test
    void updateSynchronizesChangedPhoneWithAccountUsername() {
        Map<String, Object> original = memberRow(41L);
        when(db.select("member", Map.of("member_id", "eq.41"), null, 1)).thenReturn(List.of(original));
        Map<String, Object> saved = memberRow(41L);
        saved.put("phone", "0907654321");
        when(db.update(eq("member"), eq("member_id"), eq(41L), anyMap())).thenReturn(List.of(saved));
        when(db.update(eq("account"), eq("member_id"), eq(41L), anyMap())).thenReturn(List.of());

        service.update(41L, Map.of("phone", "0907654321", "fullName", "Nguyen Van An", "gender", "Male"));

        verify(db).update("account", "member_id", 41L,
                Map.of("username", "0907654321", "full_name", "Nguyen Van An"));
    }

    private static Map<String, Object> validMember() {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Nguyen Van An");
        body.put("gender", "Male");
        body.put("dateOfBirth", "1995-02-11");
        body.put("phone", "0901234567");
        body.put("email", "an@example.com");
        body.put("trainingObjective", "Strength");
        return body;
    }

    private static Map<String, Object> memberRow(long id) {
        Map<String, Object> row = new HashMap<>();
        row.put("member_id", id);
        row.put("full_name", "Nguyen Van An");
        row.put("gender", "Male");
        row.put("date_of_birth", "1995-02-11");
        row.put("phone", "0901234567");
        row.put("status", "Active");
        return row;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
    }
}
