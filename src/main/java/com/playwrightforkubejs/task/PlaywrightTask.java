package com.playwrightforkubejs.task;

import com.playwrightforkubejs.client.ClientRuntime;
import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.PlaywrightException;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.util.HideFromJS;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlaywrightTask<T> {
    /** Client-thread execution and generation ownership, injectable without a Minecraft instance. */
    public interface Dispatcher {
        void dispatch(Runnable action);

        long generation();

        void track(PlaywrightTask<?> task);
    }

    private static final Dispatcher CLIENT = new Dispatcher() {
        @Override
        public void dispatch(Runnable action) {
            ClientRuntime.dispatch(action);
        }

        @Override
        public long generation() {
            return ClientRuntime.generation();
        }

        @Override
        public void track(PlaywrightTask<?> task) {
            ClientRuntime.track(task);
        }
    };

    private final CompletableFuture<T> future = new CompletableFuture<>();
    private final long generation;
    private final Dispatcher dispatcher;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile PlaywrightTask<?> parent;
    private volatile PlaywrightTask<?> adopted;

    private PlaywrightTask(long generation, Dispatcher dispatcher) {
        this.generation = generation;
        this.dispatcher = dispatcher;
        future.whenComplete((value, error) -> {
            Throwable cause = unwrapError(error);
            if (isInterruption(cause)) {
                cancelled.set(cause instanceof PlaywrightException exception && exception.getCode() == ErrorCode.CANCELLED);
                stopDependency(parent, cause);
                stopDependency(adopted, cause);
            }
        });
        dispatcher.track(this);
    }

    public static <T> PlaywrightTask<T> pending(long generation) {
        return pending(generation, CLIENT);
    }

    @HideFromJS
    public static <T> PlaywrightTask<T> pending(long generation, Dispatcher dispatcher) {
        return new PlaywrightTask<>(generation, dispatcher);
    }

    public static <T> PlaywrightTask<T> resolved(T value, long generation) {
        PlaywrightTask<T> task = pending(generation);
        task.complete(value);
        return task;
    }

    public static <T> PlaywrightTask<T> failed(Throwable error, long generation) {
        PlaywrightTask<T> task = pending(generation);
        task.fail(error);
        return task;
    }

    @HideFromJS
    public void onComplete(java.util.function.BiConsumer<? super T, ? super Throwable> observer) {
        future.whenComplete((value, error) -> observer.accept(value, unwrapError(error)));
    }

    public long generation() {
        return generation;
    }

    public boolean isDone() {
        return future.isDone();
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean cancel() {
        return future.completeExceptionally(new PlaywrightException(ErrorCode.CANCELLED, "Playwright task was cancelled"));
    }

    public PlaywrightTask<T> timeout(long milliseconds) {
        if (milliseconds <= 0) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "timeout must be greater than zero");
        }
        CompletableFuture.delayedExecutor(milliseconds, TimeUnit.MILLISECONDS).execute(() ->
            fail(new PlaywrightException(ErrorCode.TIMEOUT, "Playwright task timed out after " + milliseconds + " ms"))
        );
        return this;
    }

    @HideFromJS
    public Optional<T> value() {
        if (!future.isDone() || future.isCompletedExceptionally() || future.isCancelled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(future.getNow(null));
    }

    // Hide the SAM overload from Rhino: JS must not select java.util.function.Function by accident.
    @HideFromJS
    public <R> PlaywrightTask<R> then(java.util.function.Function<? super T, ? extends R> callback) {
        if (callback == null) {
            return invalidCallback();
        }
        return chain(callback, false);
    }

    // Scriptable preserves real JS values without adapting plain objects into Function proxies.
    public PlaywrightTask<Object> then(Scriptable callback) {
        return then((Object) callback);
    }

    public PlaywrightTask<Object> then(Object callback) {
        try {
            Function function = RhinoCallbacks.requireFunction(callback);
            return chain(value -> RhinoCallbacks.invoke(function, value), false);
        } catch (Throwable error) {
            return failedChild(error);
        }
    }

    public PlaywrightTask<Object> catchError(Scriptable callback) {
        return catchError((Object) callback);
    }

    public PlaywrightTask<Object> catchError(Object callback) {
        try {
            Function function = RhinoCallbacks.requireFunction(callback);
            return chain(value -> RhinoCallbacks.invoke(function, value), true);
        } catch (Throwable error) {
            return failedChild(error);
        }
    }

    public PlaywrightTask<Object> onError(Scriptable callback) {
        return catchError(callback);
    }

    public PlaywrightTask<Object> onError(Object callback) {
        return catchError(callback);
    }

    @HideFromJS
    public void complete(T value) {
        if (current()) {
            future.complete(value);
        }
    }

    @HideFromJS
    public void fail(Throwable error) {
        future.completeExceptionally(unwrapError(error));
    }

    /** Adopt a JavaScript return value, including NativeJavaObject-wrapped pending tasks. */
    @HideFromJS
    @SuppressWarnings("unchecked")
    public void completeFrom(Object value) {
        if (isDone() || !current()) {
            return;
        }
        Object unwrapped = RhinoCallbacks.unwrap(value);
        if (unwrapped instanceof PlaywrightTask<?> nested) {
            if (nested == this) {
                fail(new PlaywrightException(ErrorCode.INVALID_PARAMS, "A task cannot resolve to itself"));
                return;
            }
            if (nested.generation != generation) {
                fail(reloaded());
                return;
            }
            adopted = nested;
            // A timeout/cancel may race the callback returning its nested task.
            if (isDone()) {
                future.whenComplete((ignored, error) -> stopDependency(nested, unwrapError(error)));
                return;
            }
            nested.onComplete((nestedValue, error) -> {
                if (error == null) {
                    completeFrom(nestedValue);
                } else {
                    fail(error);
                }
            });
        } else {
            complete((T) unwrapped);
        }
    }

    private <R> PlaywrightTask<R> chain(java.util.function.Function<?, ?> callback, boolean recover) {
        PlaywrightTask<R> next = new PlaywrightTask<>(generation, dispatcher);
        next.parent = this;
        future.whenComplete((value, error) -> {
            if (next.isDone() || !next.current()) {
                return;
            }
            if (!recover && error != null) {
                next.fail(unwrapError(error));
                return;
            }
            if (recover && error == null) {
                next.completeFrom(value);
                return;
            }
            try {
                dispatcher.dispatch(() -> {
                    if (next.isDone() || !next.current()) {
                        return;
                    }
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.function.Function<Object, Object> invoke = (java.util.function.Function<Object, Object>) callback;
                        next.completeFrom(invoke.apply(recover ? unwrapError(error) : value));
                    } catch (Throwable callbackError) {
                        next.fail(callbackError);
                    }
                });
            } catch (Throwable dispatchError) {
                next.fail(dispatchError);
            }
        });
        return next;
    }

    private <R> PlaywrightTask<R> invalidCallback() {
        return failedChild(new PlaywrightException(ErrorCode.INVALID_PARAMS, "Playwright callback must be a function"));
    }

    private <R> PlaywrightTask<R> failedChild(Throwable error) {
        PlaywrightTask<R> next = new PlaywrightTask<>(generation, dispatcher);
        next.fail(error);
        return next;
    }

    private boolean current() {
        if (generation != dispatcher.generation()) {
            fail(reloaded());
            return false;
        }
        return true;
    }

    private static PlaywrightException reloaded() {
        return new PlaywrightException(ErrorCode.SCRIPT_RELOADED, "Task belongs to an old script generation");
    }

    private static boolean isInterruption(Throwable error) {
        return error instanceof PlaywrightException exception && switch (exception.getCode()) {
            case CANCELLED, TIMEOUT, SCRIPT_RELOADED -> true;
            default -> false;
        };
    }

    private static void stopDependency(PlaywrightTask<?> dependency, Throwable error) {
        if (dependency != null && !dependency.isDone() && isInterruption(error)) {
            dependency.fail(error);
        }
    }

    private static Throwable unwrapError(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }
}
