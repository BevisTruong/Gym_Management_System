package com.gym.gym_backend.session;

import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleConflictServiceTest {
    @Mock
    private SupabaseDataClient db;

    private ScheduleConflictService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleConflictService(db);
    }

    @Test
    void detectsOnlyOverlappingScheduledSessionsAndCanExcludeCurrentSession() {
        when(db.select(eq("training_session"), anyMap(), eq(null), eq(1)))
                .thenAnswer(call -> {
                    Map<String, String> filters = call.getArgument(1);
                    return filters.containsKey("session_id") ? List.of() : List.of(Map.of("session_id", 5));
                });

        assertThrows(ApiException.class, () -> service.ensureNoConflicts(4, 8, LocalDate.parse("2026-06-01"),
                LocalTime.parse("10:30"), LocalTime.parse("11:30"), null));
        service.ensureNoConflicts(4, 8, LocalDate.parse("2026-06-01"), LocalTime.parse("10:30"),
                LocalTime.parse("11:30"), 5L);
    }

    @Test
    void conflictQueryUsesStrictOverlapBoundsAndScheduledStatus() {
        when(db.select(eq("training_session"), anyMap(), eq(null), eq(1))).thenReturn(List.of());

        assertEquals(false, service.hasConflict("member_id", 4, LocalDate.parse("2026-06-01"),
                LocalTime.parse("10:00"), LocalTime.parse("11:00"), null));

        org.mockito.ArgumentCaptor<Map<String, String>> filters = org.mockito.ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(db).select(eq("training_session"), filters.capture(), eq(null), eq(1));
        assertEquals("eq.Scheduled", filters.getValue().get("status"));
        assertEquals("lt.11:00", filters.getValue().get("start_time"));
        assertEquals("gt.10:00", filters.getValue().get("end_time"));
    }

    @Test
    void availableTrainersFiltersOutBusyTrainers() {
        when(db.select("trainer", Map.of("status", "eq.Active"), "full_name.asc", 200))
                .thenReturn(List.of(Map.of("trainer_id", 8), Map.of("trainer_id", 9)));
        when(db.select(eq("training_session"), anyMap(), eq(null), eq(1)))
                .thenAnswer(call -> "eq.8".equals(((Map<?, ?>) call.getArgument(1)).get("trainer_id"))
                        ? List.of(Map.of("session_id", 3)) : List.of());

        List<Map<String, Object>> result = service.availableTrainers(LocalDate.parse("2026-06-01"),
                LocalTime.parse("10:00"), LocalTime.parse("11:00"));

        assertEquals(List.of(Map.of("trainer_id", 9)), result);
    }

    @Test
    void rejectsUnsupportedIdentityColumn() {
        assertThrows(IllegalArgumentException.class, () -> service.hasConflict("account_id", 2,
                LocalDate.parse("2026-06-01"), LocalTime.NOON, LocalTime.of(13, 0), null));
    }
}
