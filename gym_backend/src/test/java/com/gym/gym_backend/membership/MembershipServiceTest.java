package com.gym.gym_backend.membership;

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
class MembershipServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-10T03:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private SupabaseDataClient db;

    private MembershipService service;

    @BeforeEach
    void setUp() {
        service = new MembershipService(db, CLOCK);
    }

    @Test
    void registerCreatesMembershipAndCalculatesEndDateFromPlan() {
        when(db.select("member", Map.of("member_id", "eq.4"), null, 1))
                .thenReturn(List.of(Map.of("member_id", 4, "status", "Active")));
        when(db.select("membership_plan", Map.of("plan_id", "eq.2"), null, 1))
                .thenReturn(List.of(Map.of("plan_id", 2, "status", "Available", "duration_months", 3,
                        "price", 1500000)));
        when(db.select(eq("membership"), anyMap(), eq("end_date.desc"), eq(200))).thenReturn(List.of());
        when(db.select(eq("membership"), anyMap(), eq(null), eq(1))).thenReturn(List.of());
        when(db.insert(eq("membership"), anyMap())).thenAnswer(call ->
                List.of(call.getArgument(1, Map.class)));

        Map<String, Object> result = service.register(Map.of("memberId", 4, "planId", 2, "startDate", "2026-05-10"),
                false);

        assertEquals("2026-05-10", result.get("start_date"));
        assertEquals("2026-08-09", result.get("end_date"));
        assertEquals("Active", result.get("membershipStatus"));
    }

    @Test
    void registrationRejectsInactiveMemberUnavailablePlanAndPastStartDate() {
        when(db.select("member", Map.of("member_id", "eq.4"), null, 1))
                .thenReturn(List.of(Map.of("member_id", 4, "status", "Inactive")));
        assertEquals(400, assertThrows(ApiException.class,
                () -> service.register(Map.of("memberId", 4, "planId", 2, "startDate", "2026-05-10"), false))
                .status().value());

        when(db.select("member", Map.of("member_id", "eq.4"), null, 1))
                .thenReturn(List.of(Map.of("member_id", 4, "status", "Active")));
        when(db.select("membership_plan", Map.of("plan_id", "eq.2"), null, 1))
                .thenReturn(List.of(Map.of("plan_id", 2, "status", "Unavailable", "duration_months", 1,
                        "price", 100)));
        assertEquals(400, assertThrows(ApiException.class,
                () -> service.register(Map.of("memberId", 4, "planId", 2, "startDate", "2026-05-10"), false))
                .status().value());

        when(db.select("membership_plan", Map.of("plan_id", "eq.2"), null, 1))
                .thenReturn(List.of(Map.of("plan_id", 2, "status", "Available", "duration_months", 1,
                        "price", 100)));
        when(db.select(eq("membership"), anyMap(), eq("end_date.desc"), eq(200))).thenReturn(List.of());
        assertEquals(400, assertThrows(ApiException.class,
                () -> service.register(Map.of("memberId", 4, "planId", 2, "startDate", "2026-05-09"), false))
                .status().value());
        verify(db, never()).insert(eq("membership"), anyMap());
    }

    @Test
    void newRegistrationCannotBypassExistingMembershipAndRenewalRequiresEligibleWindow() {
        when(db.select("member", Map.of("member_id", "eq.4"), null, 1))
                .thenReturn(List.of(Map.of("member_id", 4, "status", "Active")));
        when(db.select("membership_plan", Map.of("plan_id", "eq.2"), null, 1))
                .thenReturn(List.of(Map.of("plan_id", 2, "status", "Available", "duration_months", 1,
                        "price", 100)));
        Map<String, Object> existing = membershipRow(12, "2026-04-10", "2026-05-20");
        when(db.select(eq("membership"), anyMap(), eq("end_date.desc"), eq(200))).thenReturn(List.of(existing));

        assertEquals(409, assertThrows(ApiException.class, () -> service.register(
                Map.of("memberId", 4, "planId", 2, "startDate", "2026-05-10"), false)).status().value());
        assertEquals(400, assertThrows(ApiException.class, () -> service.register(
                Map.of("memberId", 4, "planId", 2), true)).status().value());
    }

    @Test
    void renewalLinksPreviousPeriodAndStartsAfterCurrentEndWhenInsideSevenDays() {
        when(db.select("member", Map.of("member_id", "eq.4"), null, 1))
                .thenReturn(List.of(Map.of("member_id", 4, "status", "Active")));
        when(db.select("membership_plan", Map.of("plan_id", "eq.2"), null, 1))
                .thenReturn(List.of(Map.of("plan_id", 2, "status", "Available", "duration_months", 1,
                        "price", 100)));
        when(db.select(eq("membership"), anyMap(), eq("end_date.desc"), eq(200)))
                .thenReturn(List.of(membershipRow(12, "2026-04-10", "2026-05-15")));
        when(db.select(eq("membership"), anyMap(), eq(null), eq(1))).thenReturn(List.of());
        when(db.insert(eq("membership"), anyMap())).thenAnswer(call ->
                List.of(call.getArgument(1, Map.class)));

        Map<String, Object> created = service.register(Map.of("memberId", 4, "planId", 2), true);

        assertEquals("2026-05-16", created.get("start_date"));
        assertEquals(12L, created.get("previous_membership_id"));
    }

    @Test
    void expirationListIncludesNearAndRecentlyExpiredButExcludesMembersWithFollowingPeriods() {
        List<Map<String, Object>> candidates = List.of(
                expirationRow(1, "2026-05-17"),
                expirationRow(2, "2026-04-20"),
                expirationRow(3, "2026-05-20"));
        when(db.select(eq("membership"), anyMap(), eq("end_date.asc"), eq(500),
                eq("*,member(full_name,phone),membership_plan(name)"))).thenReturn(candidates);
        when(db.select(eq("membership"), anyMap(), eq("start_date.asc"), eq(1)))
                .thenAnswer(call -> ((Map<String, String>) call.getArgument(1)).get("member_id").equals("eq.3")
                        ? List.of(Map.of("membership_id", 99)) : List.of());

        List<Map<String, Object>> results = service.expiring();

        assertEquals(2, results.size());
        assertEquals("Active", results.get(0).get("membershipStatus"));
        assertEquals(7L, results.get(0).get("daysRemaining"));
        assertEquals("Expired", results.get(1).get("membershipStatus"));
        assertEquals(-20L, results.get(1).get("daysRemaining"));
    }

    private static Map<String, Object> membershipRow(long id, String start, String end) {
        Map<String, Object> row = new HashMap<>();
        row.put("membership_id", id);
        row.put("member_id", 4L);
        row.put("start_date", start);
        row.put("end_date", end);
        return row;
    }

    private static Map<String, Object> expirationRow(long memberId, String end) {
        Map<String, Object> row = new HashMap<>();
        row.put("membership_id", memberId + 20);
        row.put("member_id", memberId);
        row.put("start_date", "2026-03-01");
        row.put("end_date", end);
        return row;
    }
}
