package com.playwrightforkubejs.api;

import com.playwrightforkubejs.client.ClientRuntime;
import com.playwrightforkubejs.task.PlaywrightTask;
import com.playwrightforkubejs.task.RhinoCallbacks;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.Scriptable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PlaywrightApi {
    private static final PlaywrightClient CLIENT = new PlaywrightClient();

    private PlaywrightApi() {
    }

    public static PlaywrightClient client() {
        return CLIENT;
    }

    public static ExpectApi expect(Object actual) {
        return new ExpectApi(actual);
    }

    public static PlaywrightTask<?> run(String name, Scriptable callback) {
        return run(name, (Object) callback);
    }

    public static PlaywrightTask<?> run(String name, Object callback) {
        final Function function;
        try {
            function = RhinoCallbacks.requireFunction(callback);
        } catch (Throwable error) {
            return PlaywrightTask.failed(error, ClientRuntime.generation());
        }
        PlaywrightTask<Object> run = ClientRuntime.submit(() -> {
            Object value = RhinoCallbacks.invoke(function, CLIENT.page(), new ExpectApi(null));
            if (value instanceof PlaywrightTask<?>) {
                return value;
            }
            // A valid callback may return null/undefined; Map.of would turn that into an unrelated NPE.
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", name);
            result.put("result", value);
            return result;
        });
        return run.then(value -> {
            ClientRuntime.releaseInputs();
            return value;
        });
    }

    public static void reset() {
        ClientRuntime.resetForScriptReload();
    }
}
