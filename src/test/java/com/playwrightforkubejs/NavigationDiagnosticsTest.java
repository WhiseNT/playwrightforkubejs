package com.playwrightforkubejs;

import com.playwrightforkubejs.client.NavigationDiagnostics;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class NavigationDiagnosticsTest {
    @Test
    void arrivalKeepsExistingFieldsAndDoesNotClaimPathPlanning() {
        Map<String, Object> result = NavigationDiagnostics.result(true,
            Map.of("x", 2.5, "y", -60.0, "z", 3.0), 0.4, 24, 100, 0.6, 0, 0);
        assertEquals(true, result.get("arrived"));
        assertEquals("ARRIVED", result.get("reason"));
        assertEquals(false, result.get("pathPlanned"));
        assertEquals("target-follow-with-local-strafe", result.get("strategy"));
        assertEquals(0.4, result.get("distance"));
        assertEquals(0.4, result.get("bestDistance"));
        assertEquals(24L, result.get("elapsedTicks"));
    }

    @Test
    void deadlineIsAnExplicitNonArrivalWithStallEvidence() {
        Map<String, Object> result = NavigationDiagnostics.result(false,
            Map.of("x", 0.5, "y", -60.0, "z", 1.5), 3.0, 40, 40, 2.8, 9, 4);
        assertEquals(false, result.get("arrived"));
        assertEquals("DEADLINE_EXPIRED", result.get("reason"));
        assertEquals(40L, result.get("timeoutTicks"));
        assertEquals(9, result.get("stalledTicks"));
        assertEquals(4, result.get("strafeAttempts"));
        assertEquals(2.8, result.get("bestDistance"));
    }

    @Test
    void finalPositionIsASnapshotNotAMutableAlias() {
        Map<String, Object> position = new LinkedHashMap<>(Map.of("x", 1.0));
        Map<String, Object> result = NavigationDiagnostics.result(true, position, 0.0, 1, 10, 0.0, 0, 0);
        position.put("x", 99.0);
        assertEquals(Map.of("x", 1.0), result.get("finalPos"));
    }
}
