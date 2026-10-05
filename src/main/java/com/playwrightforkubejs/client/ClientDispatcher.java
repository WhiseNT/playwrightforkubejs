package com.playwrightforkubejs.client;

import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.Params;
import com.playwrightforkubejs.protocol.PlaywrightException;
import com.playwrightforkubejs.task.PlaywrightTask;
import net.minecraft.client.Minecraft;

import java.util.Map;

public final class ClientDispatcher {
    private ClientDispatcher() {
    }

    public static PlaywrightTask<Map<String, Object>> action(String name, Map<String, Object> params) {
        try {
            return releaseOnFailure(dispatchAction(name, params));
        } catch (Throwable error) {
            ClientRuntime.dispatch(KeyState::clear);
            return PlaywrightTask.failed(error, ClientRuntime.generation());
        }
    }

    private static PlaywrightTask<Map<String, Object>> releaseOnFailure(PlaywrightTask<Map<String, Object>> task) {
        task.onComplete((value, error) -> {
            if (error != null) {
                ClientRuntime.dispatch(KeyState::clear);
            }
        });
        return task;
    }

    private static PlaywrightTask<Map<String, Object>> dispatchAction(String name, Map<String, Object> params) {
        if (name.equals("input.click") || name.equals("input.double-click")) {
            return ClientInput.clickTask(params == null ? Map.of() : params, name.equals("input.double-click"));
        }
        if (name.equals("input.key-press")) {
            return ClientInput.keyPressTask(params == null ? Map.of() : params);
        }
        if (name.equals("input.key-combo")) {
            return ClientInput.keyComboTask(params == null ? Map.of() : params);
        }
        if (name.equals("move.direction")) {
            return ClientActions.moveDirectionTask(params == null ? Map.of() : params);
        }
        if (name.equals("move.to")) {
            return ClientActions.moveToTask(params == null ? Map.of() : params);
        }
        if (name.equals("input.key-hold")) {
            return ClientActions.holdKeyTask(params == null ? Map.of() : params);
        }
        if (name.equals("capture.screenshot") || name.equals("gui.screenshot")) {
            return ClientActions.screenshotTask(Params.string(params, "output", "screenshots/playwright.png"));
        }
        if (name.equals("wait.perform")) {
            long ticks = Params.integer(params, "ticks", 0);
            if (ticks <= 0) {
                ticks = Math.max(1, Math.round(Params.number(params, "seconds", 0.05) * 20.0));
            }
            return ClientWaits.ticks(ticks);
        }
        if (name.equals("chat.wait")) {
            return ClientWaits.chat(Params.string(params, "match", null), timeoutTicks(params));
        }
        if (name.equals("gui.wait-open")) {
            return ClientWaits.screenOpen(timeoutTicks(params));
        }
        if (name.equals("gui.wait-update")) {
            return ClientWaits.screenUpdate(timeoutTicks(params));
        }
        if (name.equals("inventory.wait")) {
            return ClientWaits.inventory(Params.string(params, "item", null), timeoutTicks(params));
        }
        return ClientRuntime.submit(() -> ClientActions.execute(name, params == null ? Map.of() : params));
    }

    public static PlaywrightTask<Map<String, Object>> query(String name, Map<String, Object> params) {
        return releaseOnFailure(ClientRuntime.submit(() -> ClientQueries.execute(name, params == null ? Map.of() : params)));
    }

    public static PlaywrightTask<Map<String, Object>> request(String type, String name, Map<String, Object> params) {
        return switch (type.toLowerCase()) {
            case "action" -> action(name, params);
            case "query" -> query(name, params);
            default -> PlaywrightTask.failed(new PlaywrightException(ErrorCode.INVALID_PARAMS, "type must be action or query"), ClientRuntime.generation());
        };
    }

    public static Map<String, Object> syncQuery(String name, Map<String, Object> params) {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Synchronous queries may only run on the Minecraft client thread");
        }
        return ClientQueries.execute(name, params == null ? Map.of() : params);
    }

    private static long timeoutTicks(Map<String, Object> params) {
        int timeout = Params.integer(params, "timeout", Params.integer(params, "timeoutMs", 5000));
        return Math.max(1L, Math.round(timeout / 50.0));
    }
}
