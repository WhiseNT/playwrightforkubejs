package com.playwrightforkubejs.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.EventBus;
import com.playwrightforkubejs.protocol.Params;
import com.playwrightforkubejs.protocol.PlaywrightException;
import com.playwrightforkubejs.task.PlaywrightTask;
import net.minecraft.client.InputType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** Mod-local input. Coordinates are GUI-scaled, not desktop/window pixels. */
public final class ClientInput {
    private static Screen pointerScreen;
    private static double pointerX;
    private static double pointerY;
    private static int pointerWidth;
    private static int pointerHeight;
    private static boolean pointerSet;
    private static final Map<EditBox, Selection> SELECTIONS = new WeakHashMap<>();

    private ClientInput() {
    }

    static void resetPointer() {
        pointerSet = false;
        pointerScreen = null;
        SELECTIONS.clear();
    }

    private static void requireScreen(Screen screen) {
        if (screen == null || Minecraft.getInstance().screen != screen) {
            throw new PlaywrightException(ErrorCode.GUI_NOT_OPEN, "The input target screen is no longer open");
        }
    }

    public static Map<String, Object> movePointer(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "GUI coordinates must be finite");
        }
        Minecraft minecraft = Minecraft.getInstance();
        pointerScreen = minecraft.screen;
        pointerX = x;
        pointerY = y;
        pointerWidth = minecraft.getWindow().getGuiScaledWidth();
        pointerHeight = minecraft.getWindow().getGuiScaledHeight();
        pointerSet = true;
        double cursorX = x * minecraft.getWindow().getScreenWidth() / pointerWidth;
        double cursorY = y * minecraft.getWindow().getScreenHeight() / pointerHeight;
        // Deliberately do not rely on an asynchronous GLFW cursor callback.
        if (pointerScreen != null) {
            minecraft.setLastInputType(InputType.MOUSE);
            pointerScreen.mouseMoved(x, y);
            if (minecraft.screen == pointerScreen) {
                pointerScreen.afterMouseMove();
            }
        }
        return Map.of("x", x, "y", y, "cursorX", cursorX, "cursorY", cursorY, "coordinates", "gui");
    }

    public static Map<String, Object> mousePosition() {
        Minecraft minecraft = Minecraft.getInstance();
        double rawX = minecraft.mouseHandler.xpos();
        double rawY = minecraft.mouseHandler.ypos();
        double width = minecraft.getWindow().getGuiScaledWidth();
        double height = minecraft.getWindow().getGuiScaledHeight();
        double x = pointerSet ? pointerX * width / pointerWidth : rawX * width / minecraft.getWindow().getScreenWidth();
        double y = pointerSet ? pointerY * height / pointerHeight : rawY * height / minecraft.getWindow().getScreenHeight();
        return Map.of("x", x, "y", y, "cursorX", x * minecraft.getWindow().getScreenWidth() / width,
            "cursorY", y * minecraft.getWindow().getScreenHeight() / height,
            "rawX", rawX, "rawY", rawY, "synthetic", pointerSet, "coordinates", "gui");
    }

    private static double[] pointer(Map<String, Object> params) {
        Minecraft minecraft = Minecraft.getInstance();
        if (params.containsKey("x") != params.containsKey("y")) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Provide both GUI x and y, or neither");
        }
        if (params.containsKey("x")) {
            movePointer(Params.number(params, "x", 0), Params.number(params, "y", 0));
        }
        if (!pointerSet) {
            double x = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
            double y = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
            movePointer(x, y);
        } else if (pointerScreen != minecraft.screen
            || pointerWidth != minecraft.getWindow().getGuiScaledWidth()
            || pointerHeight != minecraft.getWindow().getGuiScaledHeight()) {
            movePointer(pointerX * minecraft.getWindow().getGuiScaledWidth() / pointerWidth,
                pointerY * minecraft.getWindow().getGuiScaledHeight() / pointerHeight);
        }
        return new double[]{pointerX, pointerY};
    }

    /** A full click, never dispatching a release to an obsolete screen. */
    public static Map<String, Object> clickAt(Screen screen, double x, double y, int button) {
        requireScreen(screen);
        validateButton(button);
        movePointer(x, y);
        requireScreen(screen);
        boolean clicked = false;
        boolean released = false;
        Throwable failure = null;
        try {
            clicked = screen.mouseClicked(x, y, button);
        } catch (Throwable error) {
            failure = error;
            throw error;
        } finally {
            try {
                if (Minecraft.getInstance().screen == screen) {
                    released = screen.mouseReleased(x, y, button);
                    if (Minecraft.getInstance().screen == screen) screen.afterMouseAction();
                } else {
                    // Clear bookkeeping only: the old screen must not receive another event.
                    screen.setDragging(false);
                }
            } catch (Throwable error) {
                if (failure == null) {
                    throw error;
                }
                failure.addSuppressed(error);
            } finally {
                KeyState.releaseIfContextChanged();
            }
        }
        return Map.of("x", x, "y", y, "button", button, "handled", clicked || released,
            "pressedHandled", clicked, "releasedHandled", released,
            "screenChanged", Minecraft.getInstance().screen != screen, "coordinates", "gui");
    }

    private static void validateButton(int button) {
        if (button < 0 || button > 2) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Mouse button must be 0, 1, or 2");
        }
    }

    public static PlaywrightTask<Map<String, Object>> clickTask(Map<String, Object> params, boolean doubleClick) {
        var result = ClientRuntime.track(PlaywrightTask.<Map<String, Object>>pending(ClientRuntime.generation()));
        result.onComplete((value, error) -> {
            if (error != null) {
                ClientRuntime.dispatch(KeyState::clear);
            } else {
                EventBus.getInstance().record("input.click", value);
            }
            ClientRuntime.untrackTask(result);
        });
        ClientRuntime.dispatch(() -> {
            if (result.isDone() || result.generation() != ClientRuntime.generation()) {
                return;
            }
            try {
                int button = Params.integer(params, "button", 0);
                validateButton(button);
                Minecraft minecraft = Minecraft.getInstance();
                Screen screen = minecraft.screen;
                double[] position = pointer(params);
                Object level = minecraft.level;
                Object player = minecraft.player;
                if (screen == null && (player == null || level == null)) {
                    throw new PlaywrightException(ErrorCode.NOT_IN_WORLD, "Mouse click requires a screen or an active world");
                }
                clickSequence(result, screen, level, player, position, button, doubleClick, 0, false);
            } catch (Throwable error) {
                result.fail(error);
            }
        });
        return result;
    }

    private static void clickSequence(PlaywrightTask<Map<String, Object>> result, Screen screen, Object level,
                                      Object player, double[] position, int button, boolean doubleClick,
                                      int completed, boolean handled) {
        if (result.isDone()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != screen || minecraft.level != level || minecraft.player != player) {
            result.complete(Map.of("button", button, "double", doubleClick, "clicks", completed,
                "handled", handled, "screenChanged", true));
            return;
        }
        if (screen != null) {
            Map<String, Object> click = clickAt(screen, position[0], position[1], button);
            boolean nextHandled = handled || Boolean.TRUE.equals(click.get("handled"));
            if (!doubleClick || completed == 1 || minecraft.screen != screen) {
                Map<String, Object> output = new LinkedHashMap<>(click);
                output.put("double", doubleClick);
                output.put("clicks", completed + 1);
                output.put("handled", nextHandled);
                result.complete(output);
            } else {
                schedule(result, 2L, () -> clickSequence(result, screen, level, player, position, button, true, 1, nextHandled));
            }
            return;
        }
        KeyMapping mapping = button == 0 ? minecraft.options.keyAttack : button == 1 ? minecraft.options.keyUse : minecraft.options.keyPickItem;
        String name = "mouse-" + button;
        KeyState.set(mapping, true, name);
        KeyMapping.click(mapping.getKey());
        schedule(result, 1L, () -> {
            KeyState.set(mapping, false, name);
            if (doubleClick && completed == 0) {
                schedule(result, 2L, () -> clickSequence(result, screen, level, player, position, button, true, 1, true));
            } else {
                result.complete(Map.of("button", button, "double", doubleClick, "clicks", completed + 1,
                    "handled", true, "screenChanged", minecraft.screen != screen, "released", true));
            }
        });
    }

    private static void schedule(PlaywrightTask<Map<String, Object>> result, long ticks, Runnable next) {
        var scheduled = ClientRuntime.schedule(ticks, () -> {
            if (!result.isDone()) {
                next.run();
            }
            return Map.of("inputScheduled", true);
        });
        scheduled.onComplete((value, error) -> {
            if (error != null && !result.isDone()) {
                result.fail(error);
            }
        });
    }

    public static Map<String, Object> scroll(double delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen == null) {
            throw new PlaywrightException(ErrorCode.GUI_NOT_OPEN, "input.scroll requires an open screen");
        }
        double[] position = pointer(Map.of());
        requireScreen(screen);
        boolean handled = screen.mouseScrolled(position[0], position[1], delta);
        if (minecraft.screen == screen) {
            screen.afterMouseAction();
        }
        return Map.of("delta", delta, "x", position[0], "y", position[1], "handled", handled);
    }

    public static Map<String, Object> type(Screen screen, String text) {
        requireScreen(screen);
        int handled = 0;
        for (int index = 0; index < text.length(); index++) {
            requireScreen(screen);
            if (screen.charTyped(text.charAt(index), KeyState.modifiers())) {
                handled++;
            }
        }
        if (Minecraft.getInstance().screen == screen) {
            screen.afterKeyboardAction();
        }
        return Map.of("text", text, "charactersHandled", handled, "handled", handled == text.length());
    }

    /** Physical keys are separate from game actions, and Escape uses KeyboardHandler in-world. */
    public static Map<String, Object> keyState(String name, boolean pressed) {
        Minecraft minecraft = Minecraft.getInstance();
        KeyMapping mapping = gameMapping(name);
        if (mapping != null) {
            if (!pressed && KeyState.tracksPhysical(mapping.getKey())) {
                return physicalKey(name, mapping.getKey(), false);
            }
            if (!pressed) {
                KeyState.set(mapping, false, name);
                return Map.of("key", name, "pressed", false, "kind", "game-action");
            }
            if (minecraft.screen != null) {
                InputConstants.Key key = mapping.getKey();
                if (key.getType() == InputConstants.Type.MOUSE) {
                    throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Use input.click for mouse bindings in a screen");
                }
                return physicalKey(name, key, pressed);
            }
            requireWorld();
            KeyState.set(mapping, pressed, name);
            if (pressed) {
                KeyMapping.click(mapping.getKey());
            }
            return Map.of("key", name, "pressed", pressed, "kind", "game-action");
        }
        return physicalKey(name, physicalKey(name), pressed);
    }

    private static Map<String, Object> physicalKey(String name, InputConstants.Key key, boolean pressed) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen == null && pressed) {
            requireWorld();
        }
        int keyCode = key.getType() == InputConstants.Type.SCANCODE ? GLFW.GLFW_KEY_UNKNOWN : key.getValue();
        int scanCode = key.getType() == InputConstants.Type.SCANCODE ? key.getValue() : 0;
        KeyState.physical(key, pressed, name, screen);
        boolean handled = false;
        try {
            if (screen != null) {
                if (pressed) {
                    handled = pressKey(screen, keyCode, scanCode, KeyState.modifiers());
                } else if (KeyState.releaseTarget(key) == screen) {
                    handled = screen.keyReleased(keyCode, scanCode, KeyState.modifiers());
                    if (minecraft.screen == screen) screen.afterKeyboardAction();
                }
            } else {
                minecraft.keyboardHandler.keyPress(minecraft.getWindow().getWindow(), keyCode, scanCode,
                    pressed ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, KeyState.modifiers());
                handled = true;
            }
            if (!pressed) {
                // KeyboardHandler omits KeyMapping release when any screen is open.
                KeyMapping.set(key, false);
                for (KeyMapping mapping : minecraft.options.keyMappings) {
                    if (mapping.getKey().equals(key)) KeyState.set(mapping, false, name);
                }
                KeyState.forgetPhysical(key);
            }
        } catch (Throwable error) {
            KeyState.clear();
            throw error;
        } finally {
            KeyState.releaseIfContextChanged();
        }
        return Map.of("key", name, "pressed", pressed, "kind", "physical", "handled", handled,
            "screenChanged", minecraft.screen != screen);
    }

    public static PlaywrightTask<Map<String, Object>> keyPressTask(Map<String, Object> params) {
        var result = ClientRuntime.track(PlaywrightTask.<Map<String, Object>>pending(ClientRuntime.generation()));
        result.onComplete((value, error) -> {
            if (error != null) {
                ClientRuntime.dispatch(KeyState::clear);
            }
            ClientRuntime.untrackTask(result);
        });
        ClientRuntime.dispatch(() -> {
            if (result.isDone() || result.generation() != ClientRuntime.generation()) {
                return;
            }
            try {
                String key = Params.requiredString(params, "key");
                Screen screen = Minecraft.getInstance().screen;
                Map<String, Object> output = keyState(key, true);
                if (screen != null || Minecraft.getInstance().screen != null) {
                    keyState(key, false);
                    result.complete(output);
                } else {
                    schedule(result, 1L, () -> {
                        keyState(key, false);
                        result.complete(output);
                    });
                }
            } catch (Throwable error) {
                result.fail(error);
            }
        });
        return result;
    }

    public static PlaywrightTask<Map<String, Object>> keyComboTask(Map<String, Object> params) {
        var result = ClientRuntime.track(PlaywrightTask.<Map<String, Object>>pending(ClientRuntime.generation()));
        result.onComplete((value, error) -> {
            if (error != null) {
                ClientRuntime.dispatch(KeyState::clear);
            }
            ClientRuntime.untrackTask(result);
        });
        ClientRuntime.dispatch(() -> {
            if (result.isDone() || result.generation() != ClientRuntime.generation()) {
                return;
            }
            try {
                if (!(params.get("keys") instanceof List<?> keys) || keys.isEmpty()) {
                    throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "input.key-combo requires non-empty keys");
                }
                // Resolve everything before pressing the first key.
                for (Object raw : keys) {
                    String key = String.valueOf(raw);
                    if (gameMapping(key) == null) physicalKey(key);
                }
                List<String> pressed = new ArrayList<>();
                Screen initialScreen = Minecraft.getInstance().screen;
                for (Object raw : keys) {
                    String key = String.valueOf(raw);
                    keyState(key, true);
                    pressed.add(key);
                    if (Minecraft.getInstance().screen != initialScreen) break;
                }
                Runnable release = () -> {
                    for (int i = pressed.size() - 1; i >= 0; i--) {
                        keyState(pressed.get(i), false);
                    }
                    result.complete(Map.of("keys", List.copyOf(pressed), "released", true,
                        "screenChanged", Minecraft.getInstance().screen != initialScreen));
                };
                if (initialScreen != null || Minecraft.getInstance().screen != null) {
                    release.run();
                } else {
                    schedule(result, 1L, release);
                }
            } catch (Throwable error) {
                result.fail(error);
            }
        });
        return result;
    }

    /** Screen event dispatch, with bounded vanilla adapters for GLFW-polled modifiers. */
    public static boolean pressKey(Screen screen, int keyCode, int scanCode, int modifiers) {
        requireScreen(screen);
        Minecraft minecraft = Minecraft.getInstance();
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            minecraft.setLastInputType(InputType.KEYBOARD_TAB);
        } else if (keyCode >= GLFW.GLFW_KEY_RIGHT && keyCode <= GLFW.GLFW_KEY_UP) {
            minecraft.setLastInputType(InputType.KEYBOARD_ARROW);
        }
        boolean handled;
        EditBox edit = focusedEditBox(screen);
        boolean control = (modifiers & (Minecraft.ON_OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL)) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (edit != null && edit.canConsumeInput() && (control || shift) && editKey(edit, keyCode, control, shift)) {
            handled = true;
        } else if (keyCode == GLFW.GLFW_KEY_TAB && shift && !Screen.hasShiftDown()) {
            ComponentPath path = screen.nextFocusPath(new FocusNavigationEvent.TabNavigation(false));
            if (path == null) {
                ComponentPath current = screen.getCurrentFocusPath();
                if (current != null) {
                    current.applyFocus(false);
                }
                path = screen.nextFocusPath(new FocusNavigationEvent.TabNavigation(false));
            }
            handled = path != null;
            if (path != null) {
                ComponentPath current = screen.getCurrentFocusPath();
                if (current != null) {
                    current.applyFocus(false);
                }
                path.applyFocus(true);
            }
        } else {
            handled = screen.keyPressed(keyCode, scanCode, modifiers);
        }
        if (minecraft.screen == screen) {
            screen.afterKeyboardAction();
        }
        return handled;
    }

    private static EditBox focusedEditBox(ContainerEventHandler root) {
        GuiEventListener focused = root.getFocused();
        if (focused instanceof EditBox edit) {
            return edit;
        }
        return focused instanceof ContainerEventHandler container ? focusedEditBox(container) : null;
    }

    private static boolean editKey(EditBox edit, int keyCode, boolean control, boolean shift) {
        int cursor = edit.getCursorPosition();
        if (control && keyCode == GLFW.GLFW_KEY_A) {
            edit.setCursorPosition(edit.getValue().length());
            edit.setHighlightPos(0);
            SELECTIONS.put(edit, new Selection(0, edit.getCursorPosition(), edit.getValue()));
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_C) {
            Minecraft.getInstance().keyboardHandler.setClipboard(edit.getHighlighted());
            return true;
        }
        if (control && (keyCode == GLFW.GLFW_KEY_X || keyCode == GLFW.GLFW_KEY_V)) {
            if (keyCode == GLFW.GLFW_KEY_X) {
                Minecraft.getInstance().keyboardHandler.setClipboard(edit.getHighlighted());
                edit.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
            } else {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                for (int index = 0; index < clipboard.length(); index++) {
                    edit.charTyped(clipboard.charAt(index), 0);
                }
            }
            SELECTIONS.remove(edit);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT
            || keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END) {
            int selectionLength = edit.getHighlighted().length();
            String value = edit.getValue();
            // Vanilla has no public selection-anchor getter. Track our own selection, never infer
            // its direction from the text (repeated substrings make that inference incorrect).
            Selection selection = SELECTIONS.get(edit);
            int anchor = shift && selection != null && selection.cursor() == cursor
                && selection.value().equals(value) && Math.abs(selection.anchor() - cursor) == selectionLength
                ? selection.anchor() : cursor;
            int target = keyCode == GLFW.GLFW_KEY_HOME ? 0 : keyCode == GLFW.GLFW_KEY_END ? value.length()
                : control ? edit.getWordPosition(keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1)
                : net.minecraft.Util.offsetByCodepoints(value, cursor, keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            edit.setCursorPosition(target);
            edit.setHighlightPos(shift ? anchor : target);
            SELECTIONS.put(edit, new Selection(shift ? anchor : target, target, value));
            return true;
        }
        if (control && (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE)) {
            if (edit.getHighlighted().isEmpty()) {
                edit.setHighlightPos(edit.getWordPosition(keyCode == GLFW.GLFW_KEY_BACKSPACE ? -1 : 1));
            }
            // Delegate deletion to vanilla so a read-only EditBox remains read-only.
            edit.keyPressed(keyCode, 0, 0);
            SELECTIONS.remove(edit);
            return true;
        }
        return false;
    }

    private record Selection(int anchor, int cursor, String value) {
    }

    static KeyMapping gameMapping(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "forward", "move-forward" -> minecraft.options.keyUp;
            case "back", "backward", "move-back" -> minecraft.options.keyDown;
            case "move-left", "strafe-left" -> minecraft.options.keyLeft;
            case "move-right", "strafe-right" -> minecraft.options.keyRight;
            case "jump" -> minecraft.options.keyJump;
            case "sneak" -> minecraft.options.keyShift;
            case "sprint" -> minecraft.options.keySprint;
            case "inventory" -> minecraft.options.keyInventory;
            case "use", "right_click" -> minecraft.options.keyUse;
            case "attack", "left_click" -> minecraft.options.keyAttack;
            default -> null;
        };
    }

    static InputConstants.Key physicalKey(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        int code = switch (key) {
            case "enter", "return" -> GLFW.GLFW_KEY_ENTER;
            case "escape", "esc" -> GLFW.GLFW_KEY_ESCAPE;
            case "tab" -> GLFW.GLFW_KEY_TAB;
            case "backspace" -> GLFW.GLFW_KEY_BACKSPACE;
            case "delete", "del" -> GLFW.GLFW_KEY_DELETE;
            case "up", "arrowup" -> GLFW.GLFW_KEY_UP;
            case "down", "arrowdown" -> GLFW.GLFW_KEY_DOWN;
            case "left", "arrowleft" -> GLFW.GLFW_KEY_LEFT;
            case "right", "arrowright" -> GLFW.GLFW_KEY_RIGHT;
            case "home" -> GLFW.GLFW_KEY_HOME;
            case "end" -> GLFW.GLFW_KEY_END;
            case "space" -> GLFW.GLFW_KEY_SPACE;
            case "shift", "leftshift" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "rightshift" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "ctrl", "control", "leftcontrol" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "rightcontrol" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "alt", "leftalt" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "rightalt" -> GLFW.GLFW_KEY_RIGHT_ALT;
            case "super", "meta", "command" -> GLFW.GLFW_KEY_LEFT_SUPER;
            default -> key.length() == 1 && Character.isLetterOrDigit(key.charAt(0)) && key.charAt(0) < 128
                ? Character.toUpperCase(key.charAt(0)) : GLFW.GLFW_KEY_UNKNOWN;
        };
        if (code == GLFW.GLFW_KEY_UNKNOWN) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Unknown physical key: " + name);
        }
        return InputConstants.Type.KEYSYM.getOrCreate(code);
    }

    private static void requireWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            throw new PlaywrightException(ErrorCode.NOT_IN_WORLD, "Game input requires an active world");
        }
    }
}
