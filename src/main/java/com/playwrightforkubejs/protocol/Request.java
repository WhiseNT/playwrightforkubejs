package com.playwrightforkubejs.protocol;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record Request(String id, String type, String name, Map<String, Object> args, long timeoutMs) {
    public Request {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        type = type == null ? "action" : type;
        args = args == null ? Map.of() : new LinkedHashMap<>(args);
        timeoutMs = timeoutMs <= 0 ? 5000L : timeoutMs;
    }
}
