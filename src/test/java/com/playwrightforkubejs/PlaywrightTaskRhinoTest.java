package com.playwrightforkubejs;

import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.PlaywrightException;
import com.playwrightforkubejs.task.PlaywrightTask;
import com.playwrightforkubejs.task.RhinoCallbacks;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.NativeJavaObject;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.Wrapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class PlaywrightTaskRhinoTest {
    private final TestDispatcher dispatcher = new TestDispatcher();
    private final Context context = Context.enter();
    private final Scriptable scope = context.initStandardObjects();

    @Test
    void callbackCanReadNestedJava17ImmutableCollections() {
        RhinoCallbacks.bindContext(scope, context);
        var snapshot = Map.of("matches", List.of(Map.of("keys", List.of("field.name"))).stream().toList());
        assertTrue(snapshot.get("matches").getClass().getName().endsWith("ListN"));
        Function callback = function("function(value) { var matches = value.get('matches');"
            + " return matches.size() + ':' + matches.get(0).get('keys').size(); }");
        assertEquals("1:1", RhinoCallbacks.invoke(callback, snapshot));
        assertEquals(1, snapshot.get("matches").size(), "snapshot itself is not mutated");
    }

    @Test
    void javascriptObjectSurvivesAsyncCallbacksWithPropertiesAndIdentity() {
        PlaywrightTask<Object> root = pending();
        bind("root", root);
        PlaywrightTask<?> chain = task(evaluate("var original = null; root.then(function() {"
            + " original = {item:'minecraft:air', count:0, nested:{damage:1}}; return original; })"
            + ".then(function(value) { if (value !== original) throw new Error('object identity lost');"
            + " value.nested.damage++; return value; })"
            + ".then(function(value) { return JSON.stringify(value); })"));
        root.complete(0);
        dispatcher.drain();
        assertEquals("{\"item\":\"minecraft:air\",\"count\":0.0,\"nested\":{\"damage\":2.0}}",
            chain.value().orElseThrow());
        assertEquals(2.0, evaluate("original.nested.damage"));
    }

    @Test
    void javascriptArraySurvivesAsyncCallbacksWithArraySemantics() {
        PlaywrightTask<Object> root = pending();
        bind("root", root);
        PlaywrightTask<?> chain = task(evaluate("var original = null; root.then(function() {"
            + " original = [{health:20}, {health:14}]; return original; })"
            + ".then(function(value) { if (!Array.isArray(value) || value !== original)"
            + " throw new Error('array identity lost'); value.push({health:8}); return value; })"
            + ".then(function(value) { return JSON.stringify(value); })"));
        root.complete(0);
        dispatcher.drain();
        assertEquals("[{\"health\":20.0},{\"health\":14.0},{\"health\":8.0}]", chain.value().orElseThrow());
    }

    @Test
    void callbackUsesOriginatingContextInsteadOfDroppingHostConfiguration() {
        RhinoCallbacks.bindContext(scope, context);
        context.setClassShutter((name, kind) -> !name.equals("java.lang.System"));
        context.setProperty("host-marker", "configured-context");
        context.addToScope(scope, "hostProbe", new BaseFunction() {
            @Override
            public Object call(Context active, Scriptable callScope, Scriptable self, Object[] args) {
                return active.getProperty("host-marker");
            }
        });
        Function callback = function("function() { return hostProbe(); }");
        assertEquals("configured-context", RhinoCallbacks.invoke(callback));
        assertThrows(RuntimeException.class,
            () -> RhinoCallbacks.invoke(function("function() { return java.lang.System.currentTimeMillis(); }")));
    }

    @Test
    void javascriptThenExecutesRealFunctionAndAdoptsWrappedDelayedTask() {
        PlaywrightTask<Object> root = pending();
        PlaywrightTask<Object> nested = pending();
        bind("root", root);
        bind("nested", nested);
        PlaywrightTask<?> chain = task(evaluate("var order = ''; root.then(function(v) { order += 'first:' + v; return nested; })"
            + ".then(function(v) { order += ',second:' + v; return v + 1; })"));

        root.complete(4);
        assertEquals("", evaluate("order"));
        dispatcher.drain();
        assertFalse(chain.isDone(), "NativeJavaObject-wrapped pending task must not resolve the chain early");
        assertEquals("first:4", evaluate("order"));
        nested.complete(9);
        assertFalse(chain.isDone(), "continuation must wait for client dispatch");
        dispatcher.drain();
        assertEquals("first:4,second:9", evaluate("order"));
        assertEquals(10.0, chain.value().orElseThrow());
    }

    @Test
    void delayedCallbackRetainsScriptHelpersInClosureAfterGlobalCleanup() {
        PlaywrightTask<Object> root = pending();
        bind("root", root);
        PlaywrightTask<?> result = task(evaluate("(function() {"
            + " function helper(value) { return value + 7; }"
            + " return root.then(function(value) { return helper(value); });"
            + "})()"));
        scope.delete(context, "helper");
        root.complete(5);
        dispatcher.drain();
        assertEquals(12.0, result.value().orElseThrow());
    }

    @Test
    void javascriptArrowFunctionUsesRhinoOverloadNotJavaSam() {
        PlaywrightTask<Object> root = pending();
        bind("root", root);
        PlaywrightTask<?> result = task(evaluate("root.then(v => v * 2)"));
        root.complete(5);
        dispatcher.drain();
        assertEquals(10.0, result.value().orElseThrow());
    }

    @Test
    void wrappedFunctionAndWrappedReturnAreUnwrappedRecursively() {
        PlaywrightTask<Object> root = pending();
        PlaywrightTask<Object> nested = pending();
        bind("nested", nested);
        Function callback = function("function(v) { return nested; }");
        Object wrappedCallback = new NativeJavaObject(scope, callback, Function.class, context);
        PlaywrightTask<?> next = root.then(wrappedCallback);
        root.complete("ready");
        dispatcher.drain();
        assertFalse(next.isDone());
        nested.complete("done");
        assertEquals("done", next.value().orElseThrow());
        Wrapper doubleWrapped = () -> (Wrapper) () -> callback;
        assertSame(callback, RhinoCallbacks.requireFunction(doubleWrapped));
    }

    @Test
    void exceptionsReachCatchErrorAndCatchCanReturnDelayedTask() {
        PlaywrightTask<Object> root = pending();
        PlaywrightTask<Object> recovery = pending();
        bind("root", root);
        bind("recovery", recovery);
        PlaywrightTask<?> result = task(evaluate("var caught = false; root.then(function() { throw new Error('callback exploded'); })"
            + ".catchError(function(error) { caught = true; return recovery; })"
            + ".then(function(value) { return value + '!'; })"));
        root.complete(null);
        dispatcher.drain();
        assertEquals(Boolean.TRUE, evaluate("caught"));
        assertFalse(result.isDone());
        recovery.complete("recovered");
        dispatcher.drain();
        assertEquals("recovered!", result.value().orElseThrow());
    }

    @Test
    void invalidCallbacksFailEvenWhenSourceIsPendingOrCatchWouldBeUnused() {
        PlaywrightTask<Object> root = pending();
        assertCode(ErrorCode.INVALID_PARAMS, root.then((Object) "not a function"));
        assertCode(ErrorCode.INVALID_PARAMS, root.then((Object) null));
        assertCode(ErrorCode.INVALID_PARAMS, root.catchError((Object) 42));
        bind("root", root);
        assertCode(ErrorCode.INVALID_PARAMS, task(evaluate("root.then(42)")));
        assertCode(ErrorCode.INVALID_PARAMS, task(evaluate("root.catchError('bad')")));
        assertCode(ErrorCode.INVALID_PARAMS, task(evaluate("root.then({})")));
        assertCode(ErrorCode.INVALID_PARAMS, task(evaluate("root.catchError({})")));
    }

    @Test
    void sourceFailureIsPreservedAndNoSuccessCallbackRuns() {
        PlaywrightTask<Object> root = pending();
        bind("root", root);
        PlaywrightTask<?> result = task(evaluate("var ran = false; root.then(function() { ran = true; })"));
        PlaywrightException expected = new PlaywrightException(ErrorCode.GUI_NOT_OPEN, "missing screen");
        root.fail(expected);
        dispatcher.drain();
        assertSame(expected, error(result));
        assertEquals(Boolean.FALSE, evaluate("ran"));
    }

    @Test
    void cancellationPropagatesToPendingParentAndAlreadyAdoptedTask() {
        PlaywrightTask<Object> root = pending();
        PlaywrightTask<?> waiting = root.then(function("function(v) { return v; }"));
        assertTrue(waiting.cancel());
        assertTrue(root.isCancelled());
        assertTrue(waiting.isCancelled());
        assertCode(ErrorCode.CANCELLED, root);

        PlaywrightTask<Object> second = pending();
        PlaywrightTask<Object> nested = pending();
        bind("nested", nested);
        PlaywrightTask<?> adopted = second.then(function("function() { return nested; }"));
        second.complete(null);
        dispatcher.drain();
        assertFalse(adopted.isDone());
        adopted.cancel();
        assertCode(ErrorCode.CANCELLED, nested);
        assertCode(ErrorCode.CANCELLED, adopted);
    }

    @Test
    void timeoutPropagatesToNestedTaskAndErrorCallbackRunsOnlyOnDispatcher() throws InterruptedException {
        PlaywrightTask<Object> root = pending();
        PlaywrightTask<Object> nested = pending();
        bind("nested", nested);
        PlaywrightTask<?> adopted = root.then(function("function() { return nested; }"));
        PlaywrightTask<?> caught = adopted.catchError(function("function(error) { return 'timeout recovered'; }"));
        root.complete(null);
        dispatcher.drain();
        CountDownLatch completed = new CountDownLatch(1);
        nested.onComplete((value, error) -> completed.countDown());
        adopted.timeout(10L);
        assertTrue(completed.await(2L, TimeUnit.SECONDS));
        assertCode(ErrorCode.TIMEOUT, nested);
        assertFalse(caught.isDone());
        dispatcher.drain();
        assertEquals("timeout recovered", caught.value().orElseThrow());
    }

    @Test
    void cancelledQueuedCallbackNeverRuns() {
        PlaywrightTask<Object> root = pending();
        bind("root", root);
        PlaywrightTask<?> queued = task(evaluate("var ran = false; root.then(function() { ran = true; })"));
        root.complete("ready");
        queued.cancel();
        dispatcher.drain();
        assertEquals(Boolean.FALSE, evaluate("ran"));
        assertCode(ErrorCode.CANCELLED, queued);
    }

    @Test
    void reloadCancelsQueuedAndCompletedSourceContinuationsWithoutCallingCatch() {
        PlaywrightTask<Object> root = pending();
        bind("root", root);
        PlaywrightTask<?> chain = task(evaluate("var ran = false; root.then(function() { ran = true; })"
            + ".catchError(function() { ran = true; })"));
        root.complete("ready");
        dispatcher.reload();
        dispatcher.drain();
        assertEquals(Boolean.FALSE, evaluate("ran"));
        assertCode(ErrorCode.SCRIPT_RELOADED, chain);
        // An old resolved source must not produce fresh work after reload.
        PlaywrightTask<?> late = root.then(function("function() { ran = true; }"));
        dispatcher.drain();
        assertCode(ErrorCode.SCRIPT_RELOADED, late);
        assertEquals(Boolean.FALSE, evaluate("ran"));
    }

    @Test
    void reloadFailureObserversMayRegisterStaleTasksWithoutResetLoop() {
        PlaywrightTask<Object> root = pending();
        root.onComplete((value, failure) -> root.then(function("function() { return 1; }")));
        dispatcher.reload();
        assertCode(ErrorCode.SCRIPT_RELOADED, root);
        assertTrue(dispatcher.active.isEmpty());
    }

    @Test
    void dispatchFailureAndSelfResolutionBecomeTaskFailures() {
        PlaywrightTask<Object> root = PlaywrightTask.pending(1L, new PlaywrightTask.Dispatcher() {
            public void dispatch(Runnable action) { throw new IllegalStateException("dispatcher stopped"); }
            public long generation() { return 1L; }
            public void track(PlaywrightTask<?> task) { }
        });
        PlaywrightTask<?> chain = root.then(function("function() { return 1; }"));
        root.complete(null);
        assertEquals("dispatcher stopped", error(chain).getMessage());
        PlaywrightTask<Object> self = pending();
        self.completeFrom(self);
        assertCode(ErrorCode.INVALID_PARAMS, self);
    }

    @Test
    void javaLambdaStillRunsOnDispatcherAndRawJavaArgumentsAreScriptable() {
        PlaywrightTask<Object> root = pending();
        PlaywrightTask<?> javaChain = root.then(value -> value + " Java");
        root.complete("from");
        assertFalse(javaChain.isDone());
        dispatcher.drain();
        assertEquals("from Java", javaChain.value().orElseThrow());
        Function callback = function("function(value) { return value.getMessage(); }");
        assertEquals("failure", RhinoCallbacks.invoke(callback, new IllegalStateException("failure")));
        // A failed top call must not leak activation state into later calls.
        assertThrows(RuntimeException.class, () -> RhinoCallbacks.invoke(function("function() { throw new Error('bad'); }")));
        assertEquals(7.0, RhinoCallbacks.invoke(function("function() { return 7; }")));
    }

    @Test
    void numericEntityIdDoesNotBindToNullableMapOverload() {
        bind("entity", new EntityOverloadProbe());
        assertEquals("id:143", RhinoCallbacks.unwrap(evaluate("entity.attack(Number(143))")));
        assertEquals("filter:143.0", RhinoCallbacks.unwrap(evaluate("entity.attack({id:143})")));
    }

    @Test
    void entityTargetDispatchUnwrapsNumbersAndRejectsInvalidValues() throws Exception {
        var method = com.playwrightforkubejs.api.PageApi.EntityApi.class.getDeclaredMethod("targetParams", Object.class);
        method.setAccessible(true);
        Object wrapped = new NativeJavaObject(scope, 143.0, Double.class, context);
        assertEquals(Map.of("id", 143), method.invoke(null, wrapped));
        assertEquals(Map.of("id", 143), method.invoke(null, 143.0));
        Object filter = evaluate("({id:143, maxDistance:3})");
        assertEquals(Map.of("filter", filter), method.invoke(null, filter));
        for (Object invalid : new Object[]{null, true, "143", 143.5, Double.NaN, Double.POSITIVE_INFINITY, 2147483648.0, Map.of()}) {
            var error = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(null, invalid));
            assertEquals(ErrorCode.INVALID_PARAMS, assertInstanceOf(PlaywrightException.class, error.getCause()).getCode());
        }
        for (String name : List.of("attack", "interact", "mount")) {
            var api = com.playwrightforkubejs.api.PageApi.EntityApi.class;
            assertTrue(api.getMethod(name, int.class).isAnnotationPresent(dev.latvian.mods.rhino.util.HideFromJS.class));
            assertTrue(api.getMethod(name, Map.class).isAnnotationPresent(dev.latvian.mods.rhino.util.HideFromJS.class));
            assertFalse(api.getMethod(name, Object.class).isAnnotationPresent(dev.latvian.mods.rhino.util.HideFromJS.class));
        }
    }

    public static final class EntityOverloadProbe {
        public String attack(int id) { return "id:" + id; }
        public String attack(Map<String, Object> filter) { return "filter:" + Map.of("filter", filter).get("filter").get("id"); }
    }

    private PlaywrightTask<Object> pending() {
        return PlaywrightTask.pending(dispatcher.generation(), dispatcher);
    }

    private void bind(String name, Object value) {
        context.addToScope(scope, name, value);
    }

    private Object evaluate(String script) {
        return context.evaluateString(scope, script, "task-test.js", 1, null);
    }

    private Function function(String source) {
        return (Function) evaluate("(" + source + ")");
    }

    private static PlaywrightTask<?> task(Object value) {
        return (PlaywrightTask<?>) RhinoCallbacks.unwrap(value);
    }

    private static Throwable error(PlaywrightTask<?> task) {
        assertTrue(task.isDone(), "task must have reached a terminal state");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        task.onComplete((value, error) -> failure.set(error));
        assertNotNull(failure.get(), "task must fail instead of silently resolving");
        return failure.get();
    }

    private static void assertCode(ErrorCode expected, PlaywrightTask<?> task) {
        assertEquals(expected, assertInstanceOf(PlaywrightException.class, error(task)).getCode());
    }

    private static final class TestDispatcher implements PlaywrightTask.Dispatcher {
        private final Deque<Runnable> queue = new ArrayDeque<>();
        private final List<PlaywrightTask<?>> active = Collections.synchronizedList(new ArrayList<>());
        private long generation = 1L;

        @Override
        public synchronized void dispatch(Runnable action) {
            queue.addLast(action);
        }

        @Override
        public long generation() {
            return generation;
        }

        @Override
        public void track(PlaywrightTask<?> task) {
            if (task.generation() != generation) {
                task.fail(new PlaywrightException(ErrorCode.SCRIPT_RELOADED, "stale"));
                return;
            }
            active.add(task);
            task.onComplete((value, error) -> active.remove(task));
        }

        private void drain() {
            while (true) {
                Runnable action;
                synchronized (this) {
                    action = queue.pollFirst();
                }
                if (action == null) {
                    return;
                }
                action.run();
            }
        }

        private void reload() {
            generation++;
            for (PlaywrightTask<?> task : List.copyOf(active)) {
                task.fail(new PlaywrightException(ErrorCode.SCRIPT_RELOADED, "reload"));
            }
        }
    }
}
