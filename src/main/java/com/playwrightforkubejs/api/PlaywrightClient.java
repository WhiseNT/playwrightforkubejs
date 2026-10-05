package com.playwrightforkubejs.api;

import com.playwrightforkubejs.client.ClientDispatcher;
import com.playwrightforkubejs.client.ClientWaits;
import com.playwrightforkubejs.protocol.Params;
import com.playwrightforkubejs.task.PlaywrightTask;

import java.util.Map;

public final class PlaywrightClient {
    private final PageApi page = new PageApi(this);

    public PageApi page() {
        return page;
    }

    public PageApi getPage() {
        return page;
    }

    public PlaywrightTask<Map<String, Object>> action(String name, Map<String, Object> params) {
        return ClientDispatcher.action(name, params == null ? Map.of() : params);
    }

    public PlaywrightTask<Map<String, Object>> query(String name, Map<String, Object> params) {
        return ClientDispatcher.query(name, params == null ? Map.of() : params);
    }

    public PlaywrightTask<Map<String, Object>> waitForTimeout(long milliseconds) {
        return ClientWaits.delay(milliseconds);
    }

    public PlaywrightTask<Map<String, Object>> waitForChat(String match, long timeoutMs) {
        return ClientWaits.chat(match, Math.max(1L, Math.round(timeoutMs / 50.0)));
    }

    public PlaywrightTask<Map<String, Object>> waitForGui(long timeoutMs) {
        return ClientWaits.screenOpen(Math.max(1L, Math.round(timeoutMs / 50.0)));
    }
}
