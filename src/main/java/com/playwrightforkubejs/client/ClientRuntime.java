package com.playwrightforkubejs.client;

import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.PlaywrightException;
import com.playwrightforkubejs.task.PlaywrightTask;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class ClientRuntime {
    private static final Deque<Runnable> QUEUE = new ArrayDeque<>();
    private static final List<PlaywrightTask<?>> ACTIVE = new ArrayList<>();
    private static final List<TickJob<?>> TICK_JOBS = new ArrayList<>();
    private static final AtomicLong GENERATION = new AtomicLong(1L);
    private static final AtomicBoolean RESETTING = new AtomicBoolean();
    private static volatile long tickCounter;

    private ClientRuntime() {
    }

    public static long generation() {
        return GENERATION.get();
    }

    public static void resetForScriptReload() {
        if (!RESETTING.compareAndSet(false, true)) {
            return;
        }
        try {
            List<PlaywrightTask<?>> stale;
            synchronized (ACTIVE) {
                GENERATION.incrementAndGet();
                stale = List.copyOf(ACTIVE);
                ACTIVE.clear();
            }
            synchronized (QUEUE) {
                QUEUE.clear();
            }
            synchronized (TICK_JOBS) {
                TICK_JOBS.clear();
            }
            // Never complete futures under ACTIVE's lock: completion may synchronously register tasks.
            for (PlaywrightTask<?> task : stale) {
                task.fail(reloaded());
            }
            dispatch(KeyState::clear);
        } finally {
            RESETTING.set(false);
        }
    }

    public static void enqueue(Runnable action) {
        synchronized (QUEUE) {
            QUEUE.addLast(action);
        }
    }

    public static void dispatch(Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            action.run();
        } else {
            enqueue(action);
        }
    }

    public static void releaseInputs() {
        dispatch(KeyState::clear);
    }

    public static <T> PlaywrightTask<T> track(PlaywrightTask<T> task) {
        boolean stale;
        synchronized (ACTIVE) {
            stale = task.generation() != generation();
            if (!stale && !task.isDone() && !ACTIVE.contains(task)) {
                ACTIVE.add(task);
                task.onComplete((value, error) -> {
                    untrack(task);
                    if (error != null) {
                        dispatch(KeyState::clear);
                    }
                });
            }
        }
        if (stale) {
            task.fail(reloaded());
        }
        return task;
    }

    public static void untrackTask(PlaywrightTask<?> task) {
        untrack(task);
    }

    public static <T> PlaywrightTask<T> submit(Supplier<T> action) {
        PlaywrightTask<T> task = PlaywrightTask.pending(generation());
        try {
            dispatch(() -> execute(task, action));
        } catch (Throwable error) {
            task.fail(error);
        }
        return task;
    }

    public static <T> PlaywrightTask<T> schedule(long delayTicks, Supplier<T> action) {
        PlaywrightTask<T> task = PlaywrightTask.pending(generation());
        if (!task.isDone()) {
            synchronized (TICK_JOBS) {
                TICK_JOBS.add(new TickJob<>(tickCounter + Math.max(0L, delayTicks), task, action));
            }
        }
        return task;
    }

    public static void tick() {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        tickCounter++;
        while (true) {
            Runnable action;
            synchronized (QUEUE) {
                action = QUEUE.pollFirst();
            }
            if (action == null) {
                break;
            }
            try {
                action.run();
            } catch (Throwable ignored) {
                // Submitted work and task continuations own their failure channel.
            }
        }
        List<TickJob<?>> due = new ArrayList<>();
        synchronized (TICK_JOBS) {
            TICK_JOBS.removeIf(job -> {
                if (job.task.isDone()) {
                    return true;
                }
                if (job.dueTick <= tickCounter) {
                    due.add(job);
                    return true;
                }
                return false;
            });
        }
        for (TickJob<?> job : due) {
            complete(job);
        }
    }

    private static <T> void execute(PlaywrightTask<T> task, Supplier<T> action) {
        if (task.isDone()) {
            return;
        }
        if (task.generation() != generation()) {
            task.fail(reloaded());
            return;
        }
        try {
            task.completeFrom(action.get());
        } catch (Throwable error) {
            task.fail(error);
        }
    }

    private static <T> void complete(TickJob<T> job) {
        execute(job.task, job.action);
    }

    private static PlaywrightException reloaded() {
        return new PlaywrightException(ErrorCode.SCRIPT_RELOADED, "KubeJS client scripts were reloaded");
    }

    private static void untrack(PlaywrightTask<?> task) {
        synchronized (ACTIVE) {
            ACTIVE.remove(task);
        }
    }

    private record TickJob<T>(long dueTick, PlaywrightTask<T> task, Supplier<T> action) {
    }
}
