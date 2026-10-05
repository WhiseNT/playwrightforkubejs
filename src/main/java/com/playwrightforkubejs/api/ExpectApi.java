package com.playwrightforkubejs.api;

import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.PlaywrightException;
import com.playwrightforkubejs.task.PlaywrightTask;
import com.playwrightforkubejs.task.RhinoCallbacks;
import com.playwrightforkubejs.client.GuiSelector;
import dev.latvian.mods.rhino.Undefined;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ExpectApi {
    private final Object actual;

    public ExpectApi(Object actual) {
        this.actual = RhinoCallbacks.unwrap(actual);
    }

    public PlaywrightTask<Map<String, Object>> toBeTruthy() {
        return resolve().then(value -> {
            if (!truthy(value)) {
                throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Expected value to be truthy, got: " + value);
            }
            return Map.of("asserted", true);
        });
    }

    public PlaywrightTask<Map<String, Object>> toEqual(Object expected) {
        return resolve().then(value -> {
            if (!Objects.equals(RhinoCallbacks.unwrap(value), RhinoCallbacks.unwrap(expected))) {
                throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Expected " + expected + ", got " + value);
            }
            return Map.of("asserted", true);
        });
    }

    public PlaywrightTask<Map<String, Object>> toHaveText(String expected) {
        PlaywrightTask<Object> source = actual instanceof LocatorApi locator
            ? locator.textContent().then(value -> value.get("text"))
            : resolve();
        return source.then(value -> {
            if (!String.valueOf(value).contains(expected)) {
                throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Expected text containing '" + expected + "', got " + value);
            }
            return Map.of("asserted", true);
        });
    }

    public PlaywrightTask<Map<String, Object>> toHaveCount(int expected) {
        PlaywrightTask<Map<String, Object>> source = actual instanceof LocatorApi locator
            ? locator.count()
            : resolve().then(value -> Map.of("count", countValue(value)));
        return source.then(value -> {
            int actualCount = ((Number) value.getOrDefault("count", 0)).intValue();
            if (actualCount != expected) {
                throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Expected count " + expected + ", got " + actualCount);
            }
            return Map.of("asserted", true, "count", actualCount);
        });
    }

    public PlaywrightTask<Map<String, Object>> toHaveItem(String expected) {
        return resolve().then(value -> {
            if (!(value instanceof Map<?, ?> map) || !containsItem(map, expected)) {
                throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Expected inventory item " + expected + ", got " + value);
            }
            return Map.of("asserted", true);
        });
    }

    public PlaywrightTask<Map<String, Object>> toBeVisible() {
        if (actual instanceof LocatorApi locator) {
            if (GuiSelector.isGui(locator.selector())) {
                return locator.snapshot().then(value -> {
                    Object raw = value.get("matches");
                    if (!(raw instanceof List<?> matches) || matches.size() != 1
                        || !(matches.get(0) instanceof Map<?, ?> widget)
                        || !Boolean.TRUE.equals(widget.get("visible"))) {
                        throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Expected one visible GUI control: " + locator.selector());
                    }
                    return Map.of("asserted", true);
                });
            }
            return locator.count().then(value -> {
                int count = ((Number) value.getOrDefault("count", 0)).intValue();
                if (count <= 0) {
                    throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Expected locator to be visible: " + locator.selector());
                }
                return Map.of("asserted", true, "count", count);
            });
        }
        return resolve().then(value -> {
            if (!truthy(value)) {
                throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Expected value to be visible");
            }
            return Map.of("asserted", true);
        });
    }

    private PlaywrightTask<Object> resolve() {
        if (actual instanceof LocatorApi locator) {
            return locator.snapshot().then(value -> value);
        }
        if (actual instanceof PlaywrightTask<?> task) {
            return task.then(value -> value);
        }
        return PlaywrightTask.resolved(actual, com.playwrightforkubejs.client.ClientRuntime.generation());
    }

    private static int countValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof Map<?, ?> map) {
            Object count = map.get("count");
            if (count instanceof Number number) {
                return number.intValue();
            }
            Object items = map.get("items");
            if (items instanceof List<?> list) {
                return list.size();
            }
        }
        return value == null ? 0 : 1;
    }

    private static boolean containsItem(Map<?, ?> map, String expected) {
        Object items = map.get("items");
        if (!(items instanceof List<?> list)) {
            return expected.equals(String.valueOf(map.get("item")));
        }
        return list.stream().filter(Map.class::isInstance).map(value -> (Map<?, ?>) value)
            .anyMatch(value -> expected.equals(String.valueOf(value.get("item"))));
    }

    private static boolean truthy(Object value) {
        value = RhinoCallbacks.unwrap(value);
        if (value == null || Undefined.isUndefined(value) || Boolean.FALSE.equals(value)) {
            return false;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0 && !Double.isNaN(number.doubleValue());
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        return true;
    }
}
