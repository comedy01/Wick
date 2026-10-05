package dev.wick.forge;

import dev.wick.client.DataLights;
import dev.wick.client.WickClient;
import dev.wick.client.gui.WickSettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkConstants;

@Mod(WickClient.MOD_ID)
public final class WickForge {
    public WickForge() {
        ModLoadingContext context = ModLoadingContext.get();
        context.registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, fromServer) -> true));
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        WickClient.init(FMLPaths.CONFIGDIR.get());

        KeyMapping toggle = WickClient.createToggleKey();
        WickClient.setToggleKey(toggle);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(toggle));
        modBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(DataLights.listener()));

        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (client, parent) -> new WickSettingsScreen(parent, client.options)));
    }
}
