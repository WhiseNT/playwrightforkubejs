package com.playwrightforkubejs.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class ClientHistory {
    private static final int MAX_MESSAGES = 200;
    private static final Deque<ChatMessage> CHAT = new ArrayDeque<>();
    private static long sequence;

    private ClientHistory() {
    }

    public static synchronized void addChat(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        CHAT.addLast(new ChatMessage(++sequence, message));
        while (CHAT.size() > MAX_MESSAGES) {
            CHAT.removeFirst();
        }
    }

    public static synchronized List<String> chat(int last) {
        List<String> values = CHAT.stream().map(ChatMessage::text).toList();
        if (last <= 0 || last >= values.size()) {
            return values;
        }
        return new ArrayList<>(values.subList(values.size() - last, values.size()));
    }

    public static synchronized String last() {
        ChatMessage message = CHAT.peekLast();
        return message == null ? null : message.text();
    }

    public static synchronized long nextSequence() {
        return sequence + 1L;
    }

    public static synchronized boolean containsAfter(long minimumSequence, String match) {
        for (ChatMessage message : CHAT) {
            if (message.sequence() >= minimumSequence && (match == null || message.text().contains(match))) {
                return true;
            }
        }
        return false;
    }

    public static synchronized void clear() {
        CHAT.clear();
    }

    private record ChatMessage(long sequence, String text) {
    }
}
