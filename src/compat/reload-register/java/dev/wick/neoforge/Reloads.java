package dev.wick.neoforge;

import dev.wick.client.DataLights;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

final class Reloads {
    private Reloads() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener(RegisterClientReloadListenersEvent.class, event -> event.registerReloadListener(DataLights.listener()));
    }
}
