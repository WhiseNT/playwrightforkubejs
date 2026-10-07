package com.playwrightforkubejs.client;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemData {
    private ItemData() {
    }

    public static Map<String, Object> of(ItemStack stack, int slot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("slot", slot);
        result.put("empty", stack.isEmpty());
        result.put("count", stack.getCount());
        result.put("item", stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        result.put("name", stack.isEmpty() ? "" : stack.getHoverName().getString());
        result.put("maxCount", stack.getMaxStackSize());
        result.putAll(ItemDurability.of(stack.isDamageableItem(), stack.getDamageValue(), stack.getMaxDamage()));
        return result;
    }
}
