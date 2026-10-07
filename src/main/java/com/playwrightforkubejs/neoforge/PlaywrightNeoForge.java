package com.playwrightforkubejs.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(PlaywrightNeoForge.MOD_ID)
public final class PlaywrightNeoForge {
    public static final String MOD_ID = "playwrightforkubejs";

    public PlaywrightNeoForge() {
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(ClientHooks.class);
        }
    }
}
