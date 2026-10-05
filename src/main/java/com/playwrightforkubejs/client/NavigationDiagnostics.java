package com.playwrightforkubejs.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Result metadata for the bounded target follower; does not claim path planning. */
public final class NavigationDiagnostics {
    private NavigationDiagnostics() { }

    public static Map<String, Object> result(boolean arrived, Map<String, Object> finalPos,
                                              double distance, long elapsedTicks, long timeoutTicks,
                                              double bestDistance, int stalledTicks, int strafeAttempts) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("arrived", arrived);
        result.put("finalPos", Map.copyOf(finalPos));
        result.put("distance", distance);
        result.put("reason", arrived ? "ARRIVED" : "DEADLINE_EXPIRED");
        result.put("strategy", "target-follow-with-local-strafe");
        result.put("pathPlanned", false);
        result.put("elapsedTicks", elapsedTicks);
        result.put("timeoutTicks", timeoutTicks);
        result.put("bestDistance", Math.min(bestDistance, distance));
        result.put("stalledTicks", stalledTicks);
        result.put("strafeAttempts", strafeAttempts);
        return result;
    }
}
