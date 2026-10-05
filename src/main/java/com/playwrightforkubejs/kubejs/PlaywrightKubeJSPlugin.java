package com.playwrightforkubejs.kubejs;

import com.playwrightforkubejs.api.PlaywrightApi;
import com.playwrightforkubejs.api.DevTestApi;
import com.playwrightforkubejs.client.ClientRuntime;
import com.playwrightforkubejs.task.RhinoCallbacks;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;

public final class PlaywrightKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerBindings(BindingsEvent event) {
        if (event.getType().isClient()) {
            // Bindings are rebuilt for a new client script scope. clearCaches() is global:
            // server/data reloads during world creation must not cancel client automation.
            ClientRuntime.resetForScriptReload();
            RhinoCallbacks.bindContext(event.scope, event.manager.context);
            event.add("Playwright", PlaywrightApi.class);
            if (Boolean.getBoolean("playwright.e2e")) {
                event.add("PlaywrightTest", DevTestApi.class);
            }
        }
    }

    @Override
    public void registerClasses(ScriptType type, ClassFilter filter) {
        filter.allow("com.playwrightforkubejs.api");
        filter.allow("com.playwrightforkubejs.task.PlaywrightTask");
        filter.allow("com.playwrightforkubejs.protocol.PlaywrightException");
    }

}
