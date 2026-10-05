package com.playwrightforkubejs.client;

import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.PlaywrightException;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GUI selectors deliberately do not consume the legacy chat text= selector. */
public final class GuiSelector {
    private static final Set<String> ROLES = Set.of("button", "textbox", "tab", "world", "list", "group", "widget");
    private final String kind;
    private final String value;
    private final String name;

    private GuiSelector(String kind, String value, String name) {
        this.kind = kind;
        this.value = value;
        this.name = name;
    }

    public static boolean isGui(String selector) {
        return selector != null && (selector.startsWith("gui-text=") || selector.startsWith("translation=")
            || selector.startsWith("role=") || selector.startsWith("world=") || selector.startsWith("widget="));
    }

    public static String role(String role, String name) {
        return "role=" + role + "|name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    public static GuiSelector parse(String selector) {
        if (!isGui(selector)) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Not a GUI selector: " + selector);
        }
        int separator = selector.indexOf('=');
        String kind = selector.substring(0, separator);
        String value = selector.substring(separator + 1);
        String name = null;
        if (kind.equals("role")) {
            int nameStart = value.indexOf("|name=");
            if (nameStart >= 0) {
                try {
                    name = URLDecoder.decode(value.substring(nameStart + 6), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException error) {
                    throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Invalid encoded GUI role name: " + selector);
                }
                value = value.substring(0, nameStart);
            }
            if (!ROLES.contains(value)) {
                throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Unsupported GUI role: " + value);
            }
        }
        if (value.isEmpty() && !kind.equals("gui-text")) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "GUI selector requires a value: " + selector);
        }
        return new GuiSelector(kind, value, name);
    }

    public boolean matches(Map<String, Object> node) {
        return switch (kind) {
            case "gui-text" -> value.equals(node.get("text"));
            case "translation" -> value.equals(node.get("translationKey"))
                || node.get("translationKeys") instanceof List<?> keys && keys.contains(value);
            case "role" -> value.equals(node.get("role")) && (name == null || name.equals(node.get("text")));
            case "world" -> "world".equals(node.get("role"))
                && (value.equals(node.get("worldId")) || value.equals(node.get("worldName")));
            case "widget" -> value.equals(node.get("id"));
            default -> false;
        };
    }

    public static List<Map<String, Object>> select(String selector, List<Map<String, Object>> nodes) {
        GuiSelector parsed = parse(selector);
        return nodes.stream().filter(parsed::matches).toList();
    }

    public static Map<String, Object> unique(String selector, List<Map<String, Object>> nodes, boolean actionable) {
        List<Map<String, Object>> matches = select(selector, nodes);
        if (matches.size() != 1) {
            throw new PlaywrightException(ErrorCode.INVALID_ACTION,
                "GUI locator must match exactly one control: " + selector + " (matched " + matches.size() + ")");
        }
        Map<String, Object> node = matches.get(0);
        if (actionable && (!Boolean.TRUE.equals(node.get("visible")) || !Boolean.TRUE.equals(node.get("enabled")))) {
            throw new PlaywrightException(ErrorCode.INVALID_ACTION,
                "GUI locator is hidden or disabled: " + selector + " (id=" + node.get("id") + ")");
        }
        return node;
    }

    /** Bounds are GUI-scaled coordinates, never physical window pixels. */
    public static Map<String, Object> bounds(int x, int y, int width, int height) {
        return Map.of("x", x, "y", y, "width", width, "height", height);
    }

    public static boolean intersects(int x, int y, int width, int height, int left, int top, int right, int bottom) {
        return width > 0 && height > 0 && x < right && x + width > left && y < bottom && y + height > top;
    }
}
