package com.playwrightforkubejs.neoforge;

import com.playwrightforkubejs.client.ClientHistory;
import com.playwrightforkubejs.client.ClientRuntime;
import com.playwrightforkubejs.protocol.EventBus;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Map;

public final class ClientHooks {
    private ClientHooks() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientRuntime.tick();
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
