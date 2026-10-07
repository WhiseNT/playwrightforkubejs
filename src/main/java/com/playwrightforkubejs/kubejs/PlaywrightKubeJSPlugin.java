package com.playwrightforkubejs.kubejs;

import com.playwrightforkubejs.api.DevTestApi;
import com.playwrightforkubejs.api.PlaywrightApi;
import com.playwrightforkubejs.client.ClientRuntime;
import com.playwrightforkubejs.task.RhinoCallbacks;
import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;

public final class PlaywrightKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void registerBindings(BindingRegistry event) {
        if (event.type().isClient()) {
            RhinoCallbacks.bindContext(event.scope(), event.context());
            event.add("Playwright", PlaywrightApi.class);
            if (Boolean.getBoolean("playwright.e2e")) {
                event.add("PlaywrightTest", DevTestApi.class);
            }
        }
    }

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        // KubeJS 2101 calls registerBindings once per KubeJSContext (each thread that first enters
        // the Rhino context), so it is NOT a script-reload signal and must never clear pending work.
        // A client script (re)load is the real boundary: invalidate tasks owned by the old scope.
        if (manager.scriptType.isClient()) {
            ClientRuntime.resetForScriptReload();
        }
    }

    @Override
    public void registerClasses(ClassFilter filter) {
        filter.allow("com.playwrightforkubejs.api");
        filter.allow("com.playwrightforkubejs.task.PlaywrightTask");
        filter.allow("com.playwrightforkubejs.protocol.PlaywrightException");
    }
}
