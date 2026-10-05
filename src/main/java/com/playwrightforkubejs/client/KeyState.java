package com.playwrightforkubejs.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class KeyState {
    private static final Map<KeyMapping, String> PRESSED = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<InputConstants.Key, PhysicalState> PHYSICAL = new HashMap<>();
    private static final Set<KeyMapping> TOUCHED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static Screen inputScreen;
    private static Object inputLevel;
    private static Object inputPlayer;
    private static boolean hasContext;
    private static boolean watching;
    private static long contextEpoch;

    private KeyState() {
    }

    static void set(KeyMapping mapping, boolean pressed, String name) {
        if (pressed) beginInput();
        if (!(mapping instanceof ToggleKeyMapping) || mapping.isDown() != pressed) {
            mapping.setDown(pressed);
            if (!pressed && mapping instanceof ToggleKeyMapping && mapping.isDown()) {
                // With toggle mode enabled, a second press is the normal way to turn it off.
                mapping.setDown(true);
                if (mapping.isDown()) KeyMapping.resetToggleKeys();
            }
        }
        synchronized (PRESSED) {
            if (pressed) {
                TOUCHED.add(mapping);
                PRESSED.put(mapping, name);
            } else {
                PRESSED.remove(mapping);
            }
        }
    }

    static void physical(InputConstants.Key key, boolean pressed, String name, Screen screen) {
        if (pressed) {
            beginInput();
            synchronized (PRESSED) {
                for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
                    if (mapping.getKey().equals(key)) TOUCHED.add(mapping);
                }
            }
        }
        synchronized (PHYSICAL) {
            if (pressed) {
                PHYSICAL.putIfAbsent(key, new PhysicalState(name, screen, true));
            } else {
                PhysicalState state = PHYSICAL.get(key);
                if (state != null) {
                    PHYSICAL.put(key, new PhysicalState(state.name(), state.screen(), false));
                }
            }
        }
    }

    static Screen releaseTarget(InputConstants.Key key) {
        synchronized (PHYSICAL) {
            PhysicalState state = PHYSICAL.get(key);
            return state == null ? null : state.screen();
        }
    }

    static boolean tracksPhysical(InputConstants.Key key) {
        synchronized (PHYSICAL) {
            return PHYSICAL.containsKey(key);
        }
    }

    static void forgetPhysical(InputConstants.Key key) {
        synchronized (PHYSICAL) {
            PHYSICAL.remove(key);
        }
    }

    private static void beginInput() {
        releaseIfContextChanged();
        Minecraft minecraft = Minecraft.getInstance();
        if (!hasContext) {
            inputScreen = minecraft.screen;
            inputLevel = minecraft.level;
            inputPlayer = minecraft.player;
            hasContext = true;
        }
        if (!watching) {
            watching = true;
            watchContext(contextEpoch);
        }
    }

    static void releaseIfContextChanged() {
        Minecraft minecraft = Minecraft.getInstance();
        if (hasContext && (minecraft.screen != inputScreen || minecraft.level != inputLevel || minecraft.player != inputPlayer)) {
            clear();
        }
    }

    private static void watchContext(long epoch) {
        var check = ClientRuntime.schedule(1L, () -> {
            if (epoch != contextEpoch) return Map.of("ignored", true);
            releaseIfContextChanged();
            if (epoch != contextEpoch) return Map.of("released", true);
            boolean held;
            synchronized (PRESSED) {
                held = !PRESSED.isEmpty();
            }
            synchronized (PHYSICAL) {
                held |= PHYSICAL.values().stream().anyMatch(PhysicalState::pressed);
            }
            if (held) {
                watchContext(epoch);
            } else {
                watching = false;
            }
            return Map.of("inputHeld", held);
        });
        check.onComplete((value, error) -> {
            if (error != null) {
                ClientRuntime.dispatch(() -> {
                    if (epoch == contextEpoch) clear();
                });
            }
        });
    }

    static int modifiers() {
        releaseIfContextChanged();
        int flags = 0;
        synchronized (PHYSICAL) {
            for (Map.Entry<InputConstants.Key, PhysicalState> entry : PHYSICAL.entrySet()) {
                if (!entry.getValue().pressed()) {
                    continue;
                }
                int key = entry.getKey().getValue();
                if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) flags |= GLFW.GLFW_MOD_SHIFT;
                if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) flags |= GLFW.GLFW_MOD_CONTROL;
                if (key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT) flags |= GLFW.GLFW_MOD_ALT;
                if (key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER) flags |= GLFW.GLFW_MOD_SUPER;
            }
        }
        return flags;
    }

    static List<String> keysDown() {
        releaseIfContextChanged();
        List<String> keys;
        synchronized (PRESSED) {
            keys = new ArrayList<>(PRESSED.values());
        }
        synchronized (PHYSICAL) {
            for (PhysicalState state : PHYSICAL.values()) {
                if (state.pressed()) keys.add(state.name());
            }
        }
        return keys;
    }

    static void clear() {
        contextEpoch++;
        watching = false;
        hasContext = false;
        inputScreen = null;
        inputLevel = null;
        inputPlayer = null;
        synchronized (PRESSED) {
            boolean resetToggles = false;
            for (KeyMapping mapping : TOUCHED) {
                resetToggles |= mapping instanceof ToggleKeyMapping;
                mapping.setDown(false);
                while (mapping.consumeClick()) {
                    // A failed test must not leave a queued game action for the next tick.
                }
            }
            if (resetToggles) KeyMapping.resetToggleKeys();
            PRESSED.clear();
            TOUCHED.clear();
        }
        Map<InputConstants.Key, PhysicalState> releasing;
        synchronized (PHYSICAL) {
            releasing = new HashMap<>(PHYSICAL);
            PHYSICAL.clear();
        }
        Minecraft minecraft = Minecraft.getInstance();
        for (Map.Entry<InputConstants.Key, PhysicalState> entry : releasing.entrySet()) {
            InputConstants.Key key = entry.getKey();
            KeyMapping.set(key, false);
            for (KeyMapping mapping : minecraft.options.keyMappings) {
                if (mapping.getKey().equals(key)) {
                    while (mapping.consumeClick()) {
                        // Drain only bindings touched by synthetic input.
                    }
                }
            }
            Screen target = entry.getValue().screen();
            if (target != null && minecraft.screen == target) {
                try {
                    target.keyReleased(key.getType() == InputConstants.Type.SCANCODE ? GLFW.GLFW_KEY_UNKNOWN : key.getValue(),
                        key.getType() == InputConstants.Type.SCANCODE ? key.getValue() : 0, 0);
                } catch (Throwable ignored) {
                    // Cleanup must preserve the original task failure and release all remaining keys.
                }
            }
        }
        ClientInput.resetPointer();
    }

    private record PhysicalState(String name, Screen screen, boolean pressed) {
    }
}
