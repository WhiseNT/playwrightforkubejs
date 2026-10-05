package com.playwrightforkubejs.api;

import com.playwrightforkubejs.client.ClientDispatcher;
import com.playwrightforkubejs.client.ClientWaits;
import com.playwrightforkubejs.client.GuiSelector;
import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.PlaywrightException;
import com.playwrightforkubejs.task.PlaywrightTask;

import java.util.List;
import java.util.Map;

public final class LocatorApi {
    private final PageApi page;
    private final String selector;

    LocatorApi(PageApi page, String selector) {
        this.page = page;
        this.selector = selector == null ? "" : selector;
    }

    public String selector() { return selector; }

    public PlaywrightTask<Map<String, Object>> snapshot() {
        if (GuiSelector.isGui(selector)) {
            return page.query("gui.locator", Map.of("selector", selector));
        }
        if (selector.startsWith("slot=")) {
            return page.gui().slot(parseIndex());
        }
        if (selector.startsWith("screen=")) {
            return page.gui().info();
        }
        if (selector.startsWith("item=")) {
            return page.inventory().snapshot();
        }
        if (selector.startsWith("entity=")) {
            return page.entity().list(16);
        }
        if (selector.startsWith("text=")) {
            return page.chat().history(50);
        }
        throw unsupportedSelector();
    }

    public PlaywrightTask<Map<String, Object>> click() {
        if (GuiSelector.isGui(selector)) {
            return page.action("gui.locator-click", Map.of("selector", selector));
        }
        if (selector.startsWith("slot=")) {
            return page.gui().click(parseIndex());
        }
        throw new PlaywrightException(ErrorCode.INVALID_ACTION, "Only GUI controls and slot locators can be clicked");
    }

    public PlaywrightTask<Map<String, Object>> fill(String text) {
        if (text == null) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "GUI fill text must not be null");
        }
        if (!GuiSelector.isGui(selector)) {
            throw new PlaywrightException(ErrorCode.INVALID_ACTION, "fill requires a GUI textbox locator");
        }
        return page.action("gui.locator-fill", Map.of("selector", selector, "text", text));
    }

    public PlaywrightTask<Map<String, Object>> count() {
        return snapshot().then(value -> Map.of("count", matches(value)));
    }

    public PlaywrightTask<Map<String, Object>> textContent() {
        return snapshot().then(value -> Map.of("text", text(value)));
    }

    public PlaywrightTask<Map<String, Object>> waitFor(long timeoutMs) {
        return waitFor("visible", timeoutMs);
    }

    public PlaywrightTask<Map<String, Object>> waitForVisible(long timeoutMs) { return waitFor("visible", timeoutMs); }
    public PlaywrightTask<Map<String, Object>> waitForEnabled(long timeoutMs) { return waitFor("enabled", timeoutMs); }

    public PlaywrightTask<Map<String, Object>> waitFor(String state, long timeoutMs) {
        if (!List.of("attached", "visible", "enabled", "hidden", "detached").contains(state)) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Unknown locator wait state: " + state);
        }
        long timeoutTicks = Math.max(1L, (timeoutMs + 49L) / 50L);
        if (selector.startsWith("screen=") && !state.equals("hidden") && !state.equals("detached")) {
            return ClientWaits.screen(selector.substring("screen=".length()), timeoutTicks);
        }
        return ClientWaits.until("locator.waitFor:" + selector + ":" + state,
            () -> waitMatched(state), timeoutTicks, () -> String.valueOf(snapshotSync()));
    }

    private boolean waitMatched(String state) {
        Map<String, Object> value;
        try {
            value = snapshotSync();
        } catch (PlaywrightException error) {
            if (error.getCode() == ErrorCode.GUI_NOT_OPEN || error.getCode() == ErrorCode.NOT_IN_WORLD) {
                return state.equals("hidden") || state.equals("detached");
            }
            throw error;
        }
        int count = matches(value);
        if (state.equals("detached")) {
            return count == 0;
        }
        if (!GuiSelector.isGui(selector)) {
            return state.equals("hidden") ? count == 0 : count > 0;
        }
        if (count > 1) {
            throw new PlaywrightException(ErrorCode.INVALID_ACTION, "Ambiguous GUI locator while waiting: " + selector + " (matched " + count + ")");
        }
        if (count == 0) {
            return state.equals("hidden");
        }
        Map<String, Object> node = guiMatch(value);
        return switch (state) {
            case "attached" -> true;
            case "visible" -> Boolean.TRUE.equals(node.get("visible"));
            case "enabled" -> Boolean.TRUE.equals(node.get("visible")) && Boolean.TRUE.equals(node.get("enabled"));
            case "hidden" -> !Boolean.TRUE.equals(node.get("visible"));
            default -> false;
        };
    }

    public PlaywrightTask<Map<String, Object>> screenshot(String output) {
        return page.screenshot().capture(output);
    }

    private Map<String, Object> snapshotSync() {
        if (GuiSelector.isGui(selector)) {
            return ClientDispatcher.syncQuery("gui.locator", Map.of("selector", selector));
        }
        if (selector.startsWith("slot=")) {
            return ClientDispatcher.syncQuery("gui.slot", Map.of("slot", parseIndex()));
        }
        if (selector.startsWith("screen=")) {
            return ClientDispatcher.syncQuery("gui.info", Map.of());
        }
        if (selector.startsWith("item=")) {
            return ClientDispatcher.syncQuery("inventory.get", Map.of());
        }
        if (selector.startsWith("entity=")) {
            return ClientDispatcher.syncQuery("entity.list", Map.of("radius", 16));
        }
        if (selector.startsWith("text=")) {
            return ClientDispatcher.syncQuery("chat.history", Map.of("last", 50));
        }
        throw unsupportedSelector();
    }

    private int parseIndex() {
        try {
            return Integer.parseInt(selector.substring("slot=".length()));
        } catch (NumberFormatException exception) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Slot locator must contain an integer index");
        }
    }

    private int matches(Map<String, Object> value) {
        if (GuiSelector.isGui(selector)) {
            return ((Number) value.getOrDefault("count", 0)).intValue();
        }
        if (selector.startsWith("item=")) {
            String expected = selector.substring("item=".length());
            Object rawItems = value.get("items");
            if (rawItems instanceof List<?> items) {
                return (int) items.stream().filter(Map.class::isInstance)
                    .map(item -> (Map<?, ?>) item)
                    .filter(item -> expected.equals(String.valueOf(item.get("item"))))
                    .count();
            }
            return 0;
        }
        if (selector.startsWith("entity=")) {
            String expected = selector.substring("entity=".length());
            Object rawEntities = value.get("entities");
            if (rawEntities instanceof List<?> entities) {
                return (int) entities.stream().filter(Map.class::isInstance)
                    .map(entity -> (Map<?, ?>) entity)
                    .filter(entity -> expected.equals(String.valueOf(entity.get("type"))))
                    .count();
            }
            return 0;
        }
        if (selector.startsWith("text=")) {
            String expected = selector.substring("text=".length());
            Object rawMessages = value.get("messages");
            if (rawMessages instanceof List<?> messages) {
                return (int) messages.stream().filter(String.class::isInstance)
                    .map(String.class::cast).filter(message -> message.contains(expected)).count();
            }
            return 0;
        }
        if (selector.startsWith("screen=")) {
            String expected = selector.substring("screen=".length());
            return Boolean.TRUE.equals(value.get("open"))
                && (expected.equals(value.get("type")) || expected.equals(value.get("className"))) ? 1 : 0;
        }
        return selector.startsWith("slot=") ? 1 : 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> guiMatch(Map<String, Object> value) {
        List<Map<String, Object>> matches = (List<Map<String, Object>>) value.getOrDefault("matches", List.of());
        if (matches.size() != 1) {
            throw new PlaywrightException(ErrorCode.INVALID_ACTION,
                "GUI locator must match exactly one control: " + selector + " (matched " + matches.size() + ")");
        }
        return matches.get(0);
    }

    private String text(Map<String, Object> value) {
        if (GuiSelector.isGui(selector)) {
            return String.valueOf(guiMatch(value).getOrDefault("text", ""));
        }
        if (selector.startsWith("text=")) {
            String expected = selector.substring("text=".length());
            Object rawMessages = value.get("messages");
            if (rawMessages instanceof List<?> messages) {
                return String.join("\\n", messages.stream().filter(String.class::isInstance)
                    .map(String.class::cast).filter(message -> message.contains(expected)).toList());
            }
        }
        return String.valueOf(value);
    }

    private PlaywrightException unsupportedSelector() {
        return new PlaywrightException(ErrorCode.INVALID_ACTION, "Unsupported locator selector: " + selector);
    }
}
