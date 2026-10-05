package dev.wick.fabric;

import dev.wick.client.KeyRegistrar;
import dev.wick.client.WickClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class WickFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WickClient.init(FabricLoader.getInstance().getConfigDir());
        WickClient.setToggleKey(KeyRegistrar.register(WickClient.createToggleKey()));
    }
}
