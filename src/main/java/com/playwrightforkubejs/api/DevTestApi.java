package com.playwrightforkubejs.api;

import com.google.gson.Gson;
import com.playwrightforkubejs.client.ClientQueries;
import com.playwrightforkubejs.client.ClientActions;
import com.playwrightforkubejs.client.ClientRuntime;
import net.minecraft.client.Minecraft;
import com.playwrightforkubejs.task.PlaywrightTask;
import com.playwrightforkubejs.client.ClientWaits;
import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.PlaywrightException;
import dev.latvian.mods.kubejs.client.KubeJSClient;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicitly opt-in development evidence sink, not a world creation shortcut. */
public final class DevTestApi {
    private static final Gson JSON = new Gson();
    private static boolean finished;
    private static boolean reloaded;
    private static final AtomicInteger reloadFailures = new AtomicInteger();
    private static final AtomicBoolean staleCallbackRan = new AtomicBoolean();
    private static final Map<String, String> reloadTaskErrors = new java.util.concurrent.ConcurrentHashMap<>();

    private DevTestApi() { }

    public static String runId() {
        requireEnabled();
        String id = System.getProperty("playwright.e2e.runId", "");
        if (!id.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalStateException("Invalid or missing playwright.e2e.runId");
        }
        return id;
    }

    public static String worldName() { return "PW_E2E_" + runId(); }

    public static boolean wasReloaded() {
        requireEnabled();
        return reloaded;
    }

    /** Calls KubeJS's real reload entry point, deliberately invalidating the old JS chain. */
    public static void reloadAndProbe() {
        requireEnabled();
        if (reloaded || !Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("Reload probe requires the first run on the client thread");
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            throw new IllegalStateException("Reload input probe requires an unobstructed in-world player");
        }
        long oldGeneration = ClientRuntime.generation();
        PlaywrightTask<?> oldWait = ClientWaits.ticks(200);
        PlaywrightTask<?> oldChain = oldWait.then(value -> {
            staleCallbackRan.set(true);
            return value;
        });
        PlaywrightTask<?> oldMovement = ClientActions.moveToTask(Map.of(
            "x", minecraft.player.getX(), "y", minecraft.player.getY(),
            "z", minecraft.player.getZ() + 20, "timeout", 10));
        PlaywrightTask<?> oldHold = ClientActions.holdKeyTask(Map.of("key", "sprint", "duration", 10000));
        Map<String, Object> beforeKeys = ClientQueries.execute("input.keys-down", Map.of());
        if (!minecraft.options.keyUp.isDown() || !minecraft.options.keySprint.isDown()
            || oldMovement.isDone() || oldHold.isDone()) {
            finish("FAIL", "Reload probe did not acquire active movement and sprint inputs");
            return;
        }
        oldWait.onComplete((value, error) -> observeReloadFailure("wait", error));
        oldChain.onComplete((value, error) -> observeReloadFailure("chain", error));
        oldMovement.onComplete((value, error) -> observeReloadFailure("navigation", error));
        oldHold.onComplete((value, error) -> observeReloadFailure("hold", error));
        record("reload-started", "PASS", JSON.toJson(Map.of("generation", oldGeneration,
            "keys", beforeKeys.get("keys"), "tasks", java.util.List.of("wait", "chain", "navigation", "hold"),
            "active", !oldWait.isDone() && !oldChain.isDone() && !oldMovement.isDone() && !oldHold.isDone())));
        reloaded = true;
        KubeJSClient.reloadClientScripts();
        if (ClientRuntime.generation() <= oldGeneration || !oldWait.isDone() || !oldChain.isDone()
            || !oldMovement.isDone() || !oldHold.isDone() || reloadFailures.get() != 4 || staleCallbackRan.get()
            || !((java.util.List<?>) ClientQueries.execute("input.keys-down", Map.of()).get("keys")).isEmpty()
            || minecraft.options.keyUp.isDown() || minecraft.options.keySprint.isDown()) {
            finish("FAIL", "Real KubeJS reload did not invalidate wait, chain, navigation, hold and release inputs");
            return;
        }
        record("reload-propagated", "PASS", JSON.toJson(Map.of(
            "oldGeneration", oldGeneration, "currentGeneration", ClientRuntime.generation(),
            "taskErrors", new LinkedHashMap<>(reloadTaskErrors),
            "keys", ClientQueries.execute("input.keys-down", Map.of()).get("keys"),
            "forward", minecraft.options.keyUp.isDown(), "sprint", minecraft.options.keySprint.isDown(),
            "staleCallbackRan", staleCallbackRan.get())));
    }

    public static boolean reloadProbePassed() {
        requireEnabled();
        return reloaded && reloadFailures.get() == 4 && !staleCallbackRan.get()
            && ((java.util.List<?>) ClientQueries.execute("input.keys-down", Map.of()).get("keys")).isEmpty()
            && !Minecraft.getInstance().options.keyUp.isDown() && !Minecraft.getInstance().options.keySprint.isDown();
    }

    private static void observeReloadFailure(String task, Throwable error) {
        String code = error instanceof PlaywrightException exception ? exception.getCodeName()
            : error == null ? "SUCCEEDED" : error.getClass().getName();
        reloadTaskErrors.put(task, code);
        if (error instanceof PlaywrightException exception && exception.getCode() == ErrorCode.SCRIPT_RELOADED) {
            reloadFailures.incrementAndGet();
        }
    }

    public static synchronized void record(String stage, String status, String detail) {
        requireEnabled();
        if (finished) { throw new IllegalStateException("E2E already finished"); }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("runId", runId());
        event.put("stage", stage);
        event.put("status", status);
        event.put("detail", detail);
        event.put("timeMs", System.currentTimeMillis());
        Minecraft minecraft = Minecraft.getInstance();
        event.put("thread", Thread.currentThread().getName());
        event.put("generation", ClientRuntime.generation());
        if (minecraft.isSameThread()) {
            event.put("gui", ClientQueries.execute("gui.snapshot", Map.of()));
            event.put("inWorld", minecraft.player != null && minecraft.level != null && minecraft.getConnection() != null);
            event.put("language", minecraft.options.languageCode);
            event.put("guiScale", minecraft.options.guiScale().get());
        }
        write(event);
    }

    public static synchronized void finish(String status, String detail) {
        if (!status.equals("PASS") && !status.equals("FAIL")) { throw new IllegalArgumentException("Expected PASS or FAIL"); }
        record("terminal", status, detail);
        finished = true;
        ClientRuntime.resetForScriptReload();
        if (Boolean.getBoolean("playwright.e2e.exit")) {
            Minecraft.getInstance().stop();
        }
    }

    private static void write(Map<String, Object> event) {
        Path report = Minecraft.getInstance().gameDirectory.toPath().resolve("test-results/events.jsonl");
        try {
            Files.createDirectories(report.getParent());
            Files.writeString(report, JSON.toJson(event) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot write E2E evidence", error);
        }
    }

    private static void requireEnabled() {
        if (!Boolean.getBoolean("playwright.e2e")) { throw new IllegalStateException("Development E2E is not enabled"); }
    }
}
