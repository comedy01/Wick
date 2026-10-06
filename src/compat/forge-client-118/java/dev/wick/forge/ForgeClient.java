package dev.wick.forge;

import dev.wick.client.DataLights;
import dev.wick.client.gui.WickSettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

final class ForgeClient {
    private ForgeClient() {
    }

    static void register(ModLoadingContext context, IEventBus modBus, KeyMapping toggle) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> ClientRegistry.registerKeyBinding(toggle)));
        modBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(DataLights.listener()));

        context.registerExtensionPoint(
                ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory(
                        (client, parent) -> new WickSettingsScreen(parent, client.options)));
    }
}
