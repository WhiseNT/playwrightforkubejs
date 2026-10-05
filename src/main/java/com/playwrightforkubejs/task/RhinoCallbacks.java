package com.playwrightforkubejs.task;

import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.PlaywrightException;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.latvian.mods.rhino.Wrapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RhinoCallbacks {
    private static final Object CONTEXT_KEY = new Object();

    private RhinoCallbacks() {
    }

    /** Keep the originating scope's remapper, class shutter and custom Java wrappers. */
    public static void bindContext(Scriptable scope, Context context) {
        if (!(ScriptableObject.getTopLevelScope(scope) instanceof ScriptableObject top)) {
            throw new IllegalArgumentException("Rhino top-level scope must support associated values");
        }
        Object previous = top.associateValue(CONTEXT_KEY, context);
        if (previous != context) {
            throw new IllegalStateException("Rhino scope already belongs to another Context");
        }
    }

    private static Context contextFor(Scriptable scope) {
        if (ScriptableObject.getTopLevelScope(scope) instanceof ScriptableObject top
            && top.getAssociatedValue(CONTEXT_KEY) instanceof Context context) {
            return context;
        }
        // Standalone Rhino callers do not have a KubeJS script manager.
        return Context.enter();
    }

    /** Java 17 immutable collection implementations are not publicly reflectable by Rhino. */
    private static Object scriptValue(Object value) {
        // NativeObject/NativeArray also implement Java collection interfaces.
        // They are already script values: copying them loses properties, identity and prototypes.
        if (value instanceof Scriptable) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(key, scriptValue(entry)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(entry -> copy.add(scriptValue(entry)));
            return copy;
        }
        return value;
    }

    public static Object unwrap(Object value) {
        return Wrapper.unwrapped(value);
    }

    public static Function requireFunction(Object callback) {
        Object unwrapped = unwrap(callback);
        if (!(unwrapped instanceof Function function)) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Playwright callback must be a JavaScript function");
        }
        if (function.getParentScope() == null) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Playwright callback has no JavaScript scope");
        }
        return function;
    }

    public static Object invoke(Object callback, Object... arguments) {
        Function function = requireFunction(callback);
        // callSync serializes invocation; standalone Context.enter() loses KubeJS wrappers.
        Scriptable scope = function.getParentScope();
        Context context = contextFor(scope);
        Object[] wrapped = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            wrapped[i] = Context.javaToJS(context, scriptValue(arguments[i]), scope);
        }
        return unwrap(context.callSync(function, scope, scope, wrapped));
    }
}
