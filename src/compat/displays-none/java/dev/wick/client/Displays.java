package dev.wick.client;

import dev.wick.config.WickConfig;
import net.minecraft.world.entity.Entity;

final class Displays {
    private Displays() {
    }

    static boolean is(Entity entity) {
        return false;
    }

    static int level(Entity entity, WickConfig config) {
        return 0;
    }
}
