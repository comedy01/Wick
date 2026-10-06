package dev.wick.client;

import dev.wick.config.WickConfig;
import dev.wick.mixin.BlockDisplayAccessor;
import dev.wick.mixin.ItemDisplayAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

final class Displays {
    private Displays() {
    }

    static boolean is(Entity entity) {
        return entity instanceof Display;
    }

    static int level(Entity entity, WickConfig config) {
        if (entity instanceof Display.ItemDisplay display) {
            return ItemLights.level(((ItemDisplayAccessor) display).wick$item(), EntityLights.inWater(display), config);
        }
        if (entity instanceof Display.BlockDisplay display) {
            return EntityLights.emission(((BlockDisplayAccessor) display).wick$block());
        }
        return 0;
    }
}
