package dev.wick.neoforge;

import dev.wick.client.WickClient;
import dev.wick.client.gui.WickSettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = WickClient.MOD_ID, dist = Dist.CLIENT)
public final class WickNeoForge {
    public WickNeoForge(IEventBus modBus, ModContainer container) {
        WickClient.init(FMLPaths.CONFIGDIR.get());

        KeyMapping toggle = WickClient.createToggleKey();
        WickClient.setToggleKey(toggle);
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(toggle));
        Reloads.register(modBus);

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parent) -> new WickSettingsScreen(parent, Minecraft.getInstance().options));
    }
}
