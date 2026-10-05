package com.playwrightforkubejs;

import com.playwrightforkubejs.client.ItemDurability;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ItemDurabilityTest {
    @Test
    void nonDamageableItemsAlwaysHaveZeroDurabilityNumbers() {
        for (int[] values : new int[][] {{0, 0}, {10, 100}, {-1, -10}, {Integer.MIN_VALUE, Integer.MAX_VALUE}}) {
            assertEquals(Map.of("damageable", false, "damage", 0, "maxDamage", 0, "remainingDurability", 0),
                ItemDurability.of(false, values[0], values[1]));
        }
    }

    @Test
    void undamagedItemHasFullRemainingDurability() {
        assertEquals(Map.of("damageable", true, "damage", 0, "maxDamage", 250, "remainingDurability", 250),
            ItemDurability.of(true, 0, 250));
    }

    @Test
    void damagedItemReportsRawDamageAndRemainingDurability() {
        assertEquals(Map.of("damageable", true, "damage", 17, "maxDamage", 250, "remainingDurability", 233),
            ItemDurability.of(true, 17, 250));
    }

    @Test
    void exhaustedItemHasZeroRemainingDurability() {
        assertEquals(0, ItemDurability.of(true, 250, 250).get("remainingDurability"));
    }

    @Test
    void zeroMaximumStillPreservesDamageableFlag() {
        assertEquals(Map.of("damageable", true, "damage", 0, "maxDamage", 0, "remainingDurability", 0),
            ItemDurability.of(true, 0, 0));
    }

    @Test
    void outOfRangeValuesAreNotSilentlyClamped() {
        assertEquals(Map.of("damageable", true, "damage", 251, "maxDamage", 250, "remainingDurability", -1),
            ItemDurability.of(true, 251, 250));
        assertEquals(Map.of("damageable", true, "damage", -5, "maxDamage", 250, "remainingDurability", 255),
            ItemDurability.of(true, -5, 250));
        assertEquals(Map.of("damageable", true, "damage", 5, "maxDamage", -10, "remainingDurability", -15),
            ItemDurability.of(true, 5, -10));
    }

    @Test
    void integerBoundariesRemainExactWhenRepresentable() {
        assertEquals(Integer.MAX_VALUE, ItemDurability.of(true, 0, Integer.MAX_VALUE).get("remainingDurability"));
        assertEquals(Integer.MIN_VALUE, ItemDurability.of(true, 0, Integer.MIN_VALUE).get("remainingDurability"));
        assertEquals(0, ItemDurability.of(true, Integer.MAX_VALUE, Integer.MAX_VALUE).get("remainingDurability"));
        assertEquals(0, ItemDurability.of(true, Integer.MIN_VALUE, Integer.MIN_VALUE).get("remainingDurability"));
    }

    @Test
    void unrepresentableRemainingDurabilityFailsInsteadOfWrapping() {
        assertThrows(ArithmeticException.class, () -> ItemDurability.of(true, -1, Integer.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> ItemDurability.of(true, 1, Integer.MIN_VALUE));
    }
}
