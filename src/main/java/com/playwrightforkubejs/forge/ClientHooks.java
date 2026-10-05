package com.playwrightforkubejs.forge;

import com.playwrightforkubejs.client.ClientHistory;
import com.playwrightforkubejs.client.ClientRuntime;
import com.playwrightforkubejs.protocol.EventBus;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;

public final class ClientHooks {
    private ClientHooks() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientRuntime.tick();
        }
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        Component message = event.getMessage();
        if (message != null) {
            String text = message.getString();
            ClientHistory.addChat(text);
            EventBus.getInstance().record("chat.received", Map.of("message", text));
        }
    }
}
