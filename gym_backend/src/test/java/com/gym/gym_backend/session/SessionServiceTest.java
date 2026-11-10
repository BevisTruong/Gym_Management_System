package com.gym.gym_backend.session;

import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-10T03:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private SupabaseDataClient db;

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(db, new ScheduleConflictService(db), CLOCK);
    }

    @Test
    void createValidSessionChecksActiveProfilesMembershipAndPersists() {
        validProfileAndMembership();
        when(db.select(eq("training_session"), anyMap(), eq(null), eq(1))).thenReturn(List.of());
        when(db.insert(eq("training_session"), anyMap())).thenAnswer(call -> {
            Map<String, Object> row = new HashMap<>(call.getArgument(1, Map.class));
            row.put("session_id", 14L);
            row.put("status", "Scheduled");
            return List.of(row);
        });

        Map<String, Object> result = service.create(sessionBody());

        assertEquals(14L, result.get("session_id"));
        assertEquals("Scheduled", result.get("displayStatus"));
        verify(db).insert(eq("training_session"), org.mockito.ArgumentMatchers.argThat(values ->
                "2026-05-12".equals(values.get("session_date")) && "10:00".equals(values.get("start_time"))));
    }

    @Test
    void createRejectsPastDatesOutOfHoursAndTooShortSessions() {
        Map<String, Object> past = sessionBody();
        past.put("sessionDate", "2026-05-09");
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(past)).status().value());

        Map<String, Object> closed = sessionBody();
        closed.put("startTime", "04:30");
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(closed)).status().value());

        Map<String, Object> tooShort = sessionBody();
        tooShort.put("endTime", "10:20");
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(tooShort)).status().value());
        verify(db, never()).insert(eq("training_session"), anyMap());
    }

    @Test
    void createRejectsInactiveProfilesWithoutMembershipAndScheduleConflict() {
        when(db.select("member", Map.of("member_id", "eq.4"), null, 1))
                .thenReturn(List.of(Map.of("member_id", 4, "status", "Inactive")));
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(sessionBody())).status().value());

        when(db.select("member", Map.of("member_id", "eq.4"), null, 1))
                .thenReturn(List.of(Map.of("member_id", 4, "status", "Active")));
        when(db.select("trainer", Map.of("trainer_id", "eq.8"), null, 1))
                .thenReturn(List.of(Map.of("trainer_id", 8, "status", "Active")));
        when(db.select(eq("membership"), anyMap(), eq(null), eq(1))).thenReturn(List.of());
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(sessionBody())).status().value());

        validProfileAndMembership();
        when(db.select(eq("training_session"), anyMap(), eq(null), eq(1)))
                .thenReturn(List.of(Map.of("session_id", 7)));
        assertEquals(409, assertThrows(ApiException.class, () -> service.create(sessionBody())).status().value());
        verify(db, never()).insert(eq("training_session"), anyMap());
    }

    @Test
    void cancelOnlyAllowsScheduledSessionsThatHaveNotStarted() {
        when(db.select("training_session", Map.of("session_id", "eq.14"), null, 1))
                .thenReturn(List.of(sessionRow("2026-05-12", "10:00", "11:00", "Scheduled")));
        when(db.update("training_session", "session_id", 14L, Map.of("status", "Cancelled")))
                .thenReturn(List.of(Map.of("session_id", 14, "status", "Cancelled")));

        assertEquals("Cancelled", service.cancel(14).get("status"));
        assertEquals(409, assertThrows(ApiException.class, () -> {
            when(db.select("training_session", Map.of("session_id", "eq.14"), null, 1))
                    .thenReturn(List.of(sessionRow("2026-05-10", "09:00", "10:00", "Scheduled")));
            service.cancel(14);
        }).status().value());
    }

    @Test
    void listCalculatesCompletedDisplayStatusWithoutChangingStoredStatus() {
        Map<String, Object> ended = sessionRow("2026-05-10", "09:00", "10:00", "Scheduled");
        when(db.select(eq("training_session"), anyMap(), eq("session_date.asc,start_time.asc"), eq(500),
                eq("*,member(full_name,phone,training_objective),trainer(full_name)")))
                .thenReturn(List.of(ended));

        List<Map<String, Object>> rows = service.list(null, null, null, "Completed", 500);

        assertEquals(1, rows.size());
        assertEquals("Completed", rows.getFirst().get("displayStatus"));
        assertEquals("Scheduled", rows.getFirst().get("status"));
    }

    private void validProfileAndMembership() {
        when(db.select("member", Map.of("member_id", "eq.4"), null, 1))
                .thenReturn(List.of(Map.of("member_id", 4, "status", "Active")));
        when(db.select("trainer", Map.of("trainer_id", "eq.8"), null, 1))
                .thenReturn(List.of(Map.of("trainer_id", 8, "status", "Active")));
        when(db.select("membership", Map.of("member_id", "eq.4", "start_date", "lte.2026-05-12",
                "end_date", "gte.2026-05-12"), null, 1)).thenReturn(List.of(Map.of("membership_id", 2)));
    }

    private static Map<String, Object> sessionBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("memberId", 4);
        body.put("trainerId", 8);
        body.put("sessionDate", "2026-05-12");
        body.put("startTime", "10:00");
        body.put("endTime", "11:00");
        body.put("note", "Strength training");
        return body;
    }

    private static Map<String, Object> sessionRow(String date, String start, String end, String status) {
        Map<String, Object> row = new HashMap<>();
        row.put("session_id", 14L);
        row.put("session_date", date);
        row.put("start_time", start);
        row.put("end_time", end);
        row.put("status", status);
        return row;
    }
}
