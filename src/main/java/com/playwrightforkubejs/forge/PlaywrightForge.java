package com.playwrightforkubejs.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(PlaywrightForge.MOD_ID)
public final class PlaywrightForge {
    public static final String MOD_ID = "playwrightforkubejs";

    public PlaywrightForge() {
        if (FMLEnvironment.dist.isClient()) {
            MinecraftForge.EVENT_BUS.register(ClientHooks.class);
        }
    }
}
