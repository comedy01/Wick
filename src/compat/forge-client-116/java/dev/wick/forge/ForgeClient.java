package dev.wick.forge;

import dev.wick.client.gui.WickSettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

final class ForgeClient {
    private ForgeClient() {
    }

    static void register(ModLoadingContext context, IEventBus modBus, KeyMapping toggle) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> ClientRegistry.registerKeyBinding(toggle)));

        context.registerExtensionPoint(
                ExtensionPoint.CONFIGGUIFACTORY,
                () -> (client, parent) -> new WickSettingsScreen(parent, client.options));
    }
}
