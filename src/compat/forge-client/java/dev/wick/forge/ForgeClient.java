package dev.wick.forge;

import dev.wick.client.DataLights;
import dev.wick.client.gui.WickSettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

final class ForgeClient {
    private ForgeClient() {
    }

    static void register(ModLoadingContext context, IEventBus modBus, KeyMapping toggle) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(toggle));
        modBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(DataLights.listener()));

        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (client, parent) -> new WickSettingsScreen(parent, client.options)));
    }
}
