package dev.wick.neoforge;

import dev.wick.client.DataLights;
import dev.wick.client.WickClient;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

final class Reloads {
    private Reloads() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener(AddClientReloadListenersEvent.class, event -> event.addListener(
                Identifier.fromNamespaceAndPath(WickClient.MOD_ID, "light_files"), DataLights.listener()));
    }
}
