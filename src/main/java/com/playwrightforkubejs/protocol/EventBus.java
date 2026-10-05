package com.playwrightforkubejs.protocol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class EventBus {
    private static final EventBus INSTANCE = new EventBus();

    private static final int RECENT_EVENT_LIMIT = 512;
    private final Deque<Map<String, Object>> pending = new ArrayDeque<>();
    private final Deque<Map<String, Object>> recent = new ArrayDeque<>();
    private final List<Consumer<Map<String, Object>>> listeners = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    public static EventBus getInstance() {
        return INSTANCE;
    }

    public synchronized void record(String name, Map<String, Object> data) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("seq", sequence.incrementAndGet());
        event.put("name", name);
        event.put("data", data == null ? Map.of() : new LinkedHashMap<>(data));
        pending.addLast(event);
        while (pending.size() > RECENT_EVENT_LIMIT) {
            pending.removeFirst();
        }
        recent.addLast(event);
        while (recent.size() > RECENT_EVENT_LIMIT) {
            recent.removeFirst();
        }
        for (Consumer<Map<String, Object>> listener : List.copyOf(listeners)) {
            try {
                listener.accept(event);
            } catch (RuntimeException ignored) {
                // Event listeners are observers and must not break client automation.
            }
        }
    }

    public synchronized List<Map<String, Object>> drain() {
        List<Map<String, Object>> result = new ArrayList<>(pending);
        pending.clear();
        return result;
    }

    public synchronized void subscribe(Consumer<Map<String, Object>> listener) {
        listeners.add(listener);
    }

    public synchronized void unsubscribe(Consumer<Map<String, Object>> listener) {
        listeners.remove(listener);
    }

    public synchronized Map<String, Object> eventAfter(String name, long minimumSequence) {
        for (Map<String, Object> event : recent) {
            long currentSequence = ((Number) event.get("seq")).longValue();
            if (currentSequence >= minimumSequence && name.equals(event.get("name"))) {
                return new LinkedHashMap<>(event);
            }
        }
        return null;
    }

    public synchronized long nextSequence() {
        return sequence.get() + 1;
    }
}
