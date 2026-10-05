package com.playwrightforkubejs.protocol;

import java.util.List;
import java.util.Map;

public record Response(String id, boolean ok, Map<String, Object> result, Map<String, Object> error, List<Map<String, Object>> events) {
    public static Response success(String id, Map<String, Object> result) {
        return new Response(id, true, result, null, EventBus.getInstance().drain());
    }

    public static Response failure(String id, Throwable throwable) {
        Throwable cause = throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable;
        String code = cause instanceof PlaywrightException exception ? exception.getCodeName() : ErrorCode.INTERNAL_ERROR.name();
        return new Response(id, false, null, Map.of("code", code, "message", cause.getMessage() == null ? code : cause.getMessage()), EventBus.getInstance().drain());
    }
}
