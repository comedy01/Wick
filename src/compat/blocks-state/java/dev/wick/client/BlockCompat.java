package dev.wick.client;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockCompat {
    private BlockCompat() {
    }

    public static boolean solid(BlockState state) {
        return state.isSolidRender();
    }

    static int minSection(LevelHeightAccessor level) {
        return level.getMinSectionY();
    }

    static int sections(LevelHeightAccessor level) {
        return level.getSectionsCount();
    }
}
