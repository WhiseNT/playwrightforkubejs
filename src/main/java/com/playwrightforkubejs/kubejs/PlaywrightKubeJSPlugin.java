package com.playwrightforkubejs.kubejs;

import com.playwrightforkubejs.api.DevTestApi;
import com.playwrightforkubejs.api.PlaywrightApi;
import com.playwrightforkubejs.client.ClientRuntime;
import com.playwrightforkubejs.task.RhinoCallbacks;
import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

public final class PlaywrightKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void registerBindings(BindingRegistry event) {
        if (event.type().isClient()) {
            // Bindings are rebuilt for a new client script scope. clearCaches() is global:
            // server/data reloads during world creation must not cancel client automation.
            ClientRuntime.resetForScriptReload();
            RhinoCallbacks.bindContext(event.scope(), event.context());
            event.add("Playwright", PlaywrightApi.class);
            if (Boolean.getBoolean("playwright.e2e")) {
                event.add("PlaywrightTest", DevTestApi.class);
            }
        }
    }

    @Override
    public void registerClasses(ClassFilter filter) {
        filter.allow("com.playwrightforkubejs.api");
        filter.allow("com.playwrightforkubejs.task.PlaywrightTask");
        filter.allow("com.playwrightforkubejs.protocol.PlaywrightException");
    }
}
