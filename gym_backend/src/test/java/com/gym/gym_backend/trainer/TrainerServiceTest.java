package com.gym.gym_backend.trainer;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainerServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-10T03:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private SupabaseDataClient db;

    @Mock
    private PasswordEncoder encoder;

    private TrainerService service;

    @BeforeEach
    void setUp() {
        service = new TrainerService(db, encoder, CLOCK);
    }

    @Test
    void createTrainerCreatesLinkedAccountWithTemporaryPassword() {
        when(db.insert(eq("trainer"), anyMap())).thenAnswer(call -> {
            Map<String, Object> result = new HashMap<>(call.getArgument(1, Map.class));
            result.put("trainer_id", 21L);
            return List.of(result);
        });
        when(encoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("hashed-secret");
        when(db.insert(eq("account"), anyMap())).thenAnswer(call ->
                List.of(call.getArgument(1, Map.class)));

        Map<String, Object> result = service.create(trainerBody());

        assertEquals(21L, result.get("trainer_id"));
        assertNotNull(result.get("temporaryPassword"));
        ArgumentCaptor<Map<String, Object>> account = mapCaptor();
        verify(db).insert(eq("account"), account.capture());
        assertEquals("Trainer", account.getValue().get("role"));
        assertEquals(21L, account.getValue().get("trainer_id"));
        assertEquals("hashed-secret", account.getValue().get("password_hash"));
        assertEquals(true, account.getValue().get("must_change_password"));
    }

    @Test
    void invalidTrainerInputDoesNotCreateRecord() {
        Map<String, Object> invalid = trainerBody();
        invalid.put("phone", "wrong");
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(invalid)).status().value());

        Map<String, Object> negativeExperience = trainerBody();
        negativeExperience.put("yearsOfExperience", -1);
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(negativeExperience)).status().value());
        verify(db, never()).insert(eq("trainer"), anyMap());
    }

    @Test
    void accountCreationFailureRollsBackTrainer() {
        when(db.insert(eq("trainer"), anyMap())).thenReturn(List.of(Map.of("trainer_id", 21, "phone", "0901234567")));
        when(encoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("hashed-secret");
        when(db.insert(eq("account"), anyMap())).thenThrow(new RuntimeException("account insert failed"));

        assertThrows(RuntimeException.class, () -> service.create(trainerBody()));

        verify(db).delete("trainer", "trainer_id", 21);
    }

    @Test
    void updateSynchronizesAccountIdentityAndRejectsEmptyUpdate() {
        when(db.select("trainer", Map.of("trainer_id", "eq.21"), null, 1))
                .thenReturn(List.of(Map.of("trainer_id", 21, "phone", "0901234567", "full_name", "Coach")));
        when(db.rpc(eq("update_trainer_profile"), anyMap()))
                .thenReturn(List.of(Map.of("trainer_id", 21, "phone", "0907654321", "full_name", "Coach Two")));

        service.update(21, Map.of("phone", "0907654321", "fullName", "Coach Two"));

        verify(db).rpc("update_trainer_profile", Map.of("p_id", 21L, "p_values",
                Map.of("phone", "0907654321", "full_name", "Coach Two")));
        assertEquals(400, assertThrows(ApiException.class, () -> service.update(21, Map.of())).status().value());
    }

    @Test
    void deactivationLocksAccountAndReturnsFutureScheduledSessions() {
        Map<String, Object> trainer = Map.of("trainer_id", 21, "status", "Inactive");
        when(db.select("trainer", Map.of("trainer_id", "eq.21"), null, 1)).thenReturn(List.of(trainer));
        when(db.update(eq("trainer"), eq("trainer_id"), eq(21L), anyMap())).thenReturn(List.of(trainer));
        when(db.update(eq("account"), eq("trainer_id"), eq(21L), anyMap())).thenReturn(List.of());
        when(db.select(eq("training_session"), anyMap(), eq("session_date.asc,start_time.asc"), eq(200)))
                .thenReturn(List.of(Map.of("session_id", 10)));

        Map<String, Object> result = service.status(21, "Inactive");

        assertEquals(1, ((List<?>) result.get("affectedScheduledSessions")).size());
        verify(db).update("account", "trainer_id", 21L, Map.of("status", "Locked"));
        assertEquals(400, assertThrows(ApiException.class, () -> service.status(21, "Deleted")).status().value());
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
    }

    private static Map<String, Object> trainerBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Coach One");
        body.put("phone", "0901234567");
        body.put("email", "coach@example.com");
        body.put("specialization", "Strength");
        body.put("yearsOfExperience", 5);
        return body;
    }
}
