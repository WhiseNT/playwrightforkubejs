package com.playwrightforkubejs.client;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemDurability {
    private ItemDurability() {
    }

    /** Keeps damageable item values unchanged, including out-of-range values for debugging. */
    public static Map<String, Object> of(boolean damageable, int damage, int maxDamage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("damageable", damageable);
        result.put("damage", damageable ? damage : 0);
        result.put("maxDamage", damageable ? maxDamage : 0);
        result.put("remainingDurability", damageable ? Math.subtractExact(maxDamage, damage) : 0);
        return result;
    }
}
