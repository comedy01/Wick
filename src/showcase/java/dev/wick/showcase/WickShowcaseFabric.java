package dev.wick.showcase;

import net.fabricmc.api.ClientModInitializer;

public final class WickShowcaseFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Showcase.start();
    }
}
