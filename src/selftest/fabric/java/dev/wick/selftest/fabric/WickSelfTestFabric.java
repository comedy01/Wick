package dev.wick.selftest.fabric;

import dev.wick.selftest.WickSelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class WickSelfTestFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WickSelfTest test = new WickSelfTest();
        ClientTickEvents.END_CLIENT_TICK.register(client -> test.tick());
    }
}
