package com.playwrightforkubejs.client;

import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.PlaywrightException;
import com.playwrightforkubejs.task.PlaywrightTask;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class ClientWaits {
    private ClientWaits() {
    }

    public static PlaywrightTask<Map<String, Object>> ticks(long ticks) {
        return ClientRuntime.schedule(ticks, () -> Map.of("ticks", ticks));
    }

    public static PlaywrightTask<Map<String, Object>> delay(long milliseconds) {
        if (milliseconds < 0 || milliseconds > 3_600_000L) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Delay must be between 0 and 3600000 milliseconds");
        }
        long start = System.nanoTime();
        long duration = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(milliseconds);
        return until("timeout.wait", () -> System.nanoTime() - start >= duration,
            Math.max(20L, milliseconds / 50L + 100L)).then(value -> Map.of(
                "milliseconds", milliseconds,
                "elapsedMs", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
    }

    public static PlaywrightTask<Map<String, Object>> until(String name, BooleanSupplier condition, long timeoutTicks) {
        return until(name, condition, timeoutTicks, () -> String.valueOf(ClientGui.snapshot()));
    }

    public static PlaywrightTask<Map<String, Object>> until(String name, BooleanSupplier condition, long timeoutTicks, Supplier<String> diagnostic) {
        long deadline = Math.max(1L, timeoutTicks);
        long generation = ClientRuntime.generation();
        PlaywrightTask<Map<String, Object>> task = ClientRuntime.track(PlaywrightTask.pending(generation));
        task.onComplete((value, error) -> ClientRuntime.untrackTask(task));
        ClientRuntime.schedule(1L, () -> {
            poll(task, name, condition, 0L, deadline, generation, diagnostic);
            return Map.of("started", true);
        });
        return task;
    }

    public static PlaywrightTask<Map<String, Object>> chat(String match, long timeoutTicks) {
        long startSequence = ClientHistory.nextSequence();
        return until("chat.wait", () -> ClientHistory.containsAfter(startSequence, match), timeoutTicks);
    }

    public static PlaywrightTask<Map<String, Object>> event(String name, long timeoutTicks) {
        long startSequence = com.playwrightforkubejs.protocol.EventBus.getInstance().nextSequence();
        return until("event.wait:" + name,
            () -> com.playwrightforkubejs.protocol.EventBus.getInstance().eventAfter(name, startSequence) != null,
            timeoutTicks
        ).then(waitResult -> {
            Map<String, Object> event = com.playwrightforkubejs.protocol.EventBus.getInstance().eventAfter(name, startSequence);
            return Map.of("event", event, "elapsedTicks", waitResult.get("elapsedTicks"));
        });
    }

    public static PlaywrightTask<Map<String, Object>> screenOpen(long timeoutTicks) {
        return until("gui.wait-open", () -> Minecraft.getInstance().screen != null, timeoutTicks);
    }

    public static PlaywrightTask<Map<String, Object>> screen(String expectedType, long timeoutTicks) {
        if (expectedType == null || expectedType.isBlank()) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Screen wait requires an exact type name");
        }
        return until("gui.wait-screen:" + expectedType, () -> ClientGui.screenMatches(expectedType), timeoutTicks);
    }

    public static PlaywrightTask<Map<String, Object>> ready(long timeoutTicks) {
        return until("client.ready", () -> {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.player != null && minecraft.level != null && minecraft.getConnection() != null
                && minecraft.gameMode != null && minecraft.screen == null;
        }, timeoutTicks);
    }

    public static PlaywrightTask<Map<String, Object>> screenUpdate(long timeoutTicks) {
        long deadline = Math.max(1L, timeoutTicks);
        long generation = ClientRuntime.generation();
        PlaywrightTask<Map<String, Object>> task = ClientRuntime.track(PlaywrightTask.pending(generation));
        task.onComplete((value, error) -> ClientRuntime.untrackTask(task));
        ClientRuntime.schedule(1L, () -> {
            if (Minecraft.getInstance().screen == null) {
                task.fail(new PlaywrightException(ErrorCode.GUI_NOT_OPEN, "No GUI is open to observe"));
                return Map.of("started", false);
            }
            final String initial;
            try {
                initial = String.valueOf(ClientQueries.execute("gui.snapshot", Map.of()));
            } catch (Throwable error) {
                task.fail(error);
                return Map.of("started", false);
            }
            poll(task, "gui.wait-update", () -> {
                if (Minecraft.getInstance().screen == null) {
                    return true;
                }
                return !initial.equals(String.valueOf(ClientQueries.execute("gui.snapshot", Map.of())));
            }, 0L, deadline, generation);
            return Map.of("started", true);
        });
        return task;
    }

    public static PlaywrightTask<Map<String, Object>> inventory(String item, long timeoutTicks) {
        String expected = item == null ? "" : item;
        return until("inventory.wait", () -> {
            Map<String, Object> snapshot = ClientQueries.execute("inventory.get", Map.of());
            Object rawItems = snapshot.get("items");
            if (!(rawItems instanceof List<?> items)) {
                return false;
            }
            return items.stream().filter(Map.class::isInstance).map(value -> (Map<?, ?>) value)
                .anyMatch(value -> expected.isBlank() || expected.equals(String.valueOf(value.get("item"))));
        }, timeoutTicks);
    }

    private static void poll(PlaywrightTask<Map<String, Object>> task, String name, BooleanSupplier condition, long elapsed, long deadline, long generation) {
        poll(task, name, condition, elapsed, deadline, generation, () -> String.valueOf(ClientGui.snapshot()));
    }

    private static void poll(PlaywrightTask<Map<String, Object>> task, String name, BooleanSupplier condition, long elapsed, long deadline, long generation, Supplier<String> diagnostic) {
        if (task.isDone()) {
            ClientRuntime.untrackTask(task);
            return;
        }
        if (generation != ClientRuntime.generation()) {
            task.fail(new PlaywrightException(ErrorCode.SCRIPT_RELOADED, "Wait belongs to an old script generation"));
            return;
        }
        boolean matched;
        try {
            matched = condition.getAsBoolean();
        } catch (Throwable error) {
            task.fail(error);
            return;
        }
        if (matched) {
            task.complete(Map.of("condition", name, "elapsedTicks", elapsed));
            return;
        }
        if (elapsed >= deadline) {
            String state;
            try {
                state = diagnostic.get();
            } catch (Throwable error) {
                state = "diagnostic unavailable: " + error.getMessage();
            }
            task.fail(new PlaywrightException(ErrorCode.TIMEOUT, "Timed out waiting for " + name
                + " after " + elapsed + " ticks; current=" + state));
            return;
        }
        ClientRuntime.schedule(1L, () -> {
            poll(task, name, condition, elapsed + 1L, deadline, generation, diagnostic);
            return Map.of("scheduled", true);
        });
    }
}
