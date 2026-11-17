package com.gym.gym_backend.plan;

import com.gym.gym_backend.common.exception.ApiException;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class PlanServiceTest {
    @Mock
    private SupabaseDataClient db;

    private PlanService service() {
        return new PlanService(db);
    }

    @Test
    void createValidPlanSetsAvailableAndPersistsNormalizedValues() {
        when(db.insert(eq("membership_plan"), anyMap())).thenAnswer(call ->
                List.of(call.getArgument(1, Map.class)));

        Map<String, Object> created = service().create(planBody());

        assertEquals("Available", created.get("status"));
        assertEquals(3, created.get("duration_months"));
        assertEquals(new BigDecimal("1200000"), created.get("price"));
        verify(db).insert(eq("membership_plan"), org.mockito.ArgumentMatchers.argThat(values ->
                "Three months".equals(values.get("name")) && "Available".equals(values.get("status"))));
    }

    @Test
    void createAndUpdateRejectInvalidPricingAndEmptyChanges() {
        Map<String, Object> invalid = planBody();
        invalid.put("price", 0);
        assertEquals(400, assertThrows(ApiException.class, () -> service().create(invalid)).status().value());

        when(db.select("membership_plan", Map.of("plan_id", "eq.9"), null, 1))
                .thenReturn(List.of(Map.of("plan_id", 9)));
        assertEquals(400, assertThrows(ApiException.class, () -> service().update(9, Map.of())).status().value());
        verify(db, never()).update(eq("membership_plan"), eq("plan_id"), eq(9L), anyMap());
    }

    @Test
    void statusOnlyAcceptsAvailableOrUnavailableAndMissingPlanIsNotFound() {
        assertEquals(400, assertThrows(ApiException.class, () -> service().status(3, "Active")).status().value());
        assertEquals(404, assertThrows(ApiException.class, () -> service().get(3)).status().value());
    }

    @Test
    void listAppliesOptionalStatusFilter() {
        when(db.select("membership_plan", Map.of("status", "eq.Available"), "name.asc", 200))
                .thenReturn(List.of(Map.of("plan_id", 1)));

        assertEquals(1, service().list("Available").size());
    }

    private static Map<String, Object> planBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Three months");
        body.put("durationMonths", 3);
        body.put("price", 1_200_000);
        body.put("benefits", "Gym access");
        return body;
    }
}
