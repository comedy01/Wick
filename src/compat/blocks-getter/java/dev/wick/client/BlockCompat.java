package dev.wick.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockCompat {
    private BlockCompat() {
    }

    public static boolean solid(BlockState state) {
        return state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    static int minSection(LevelHeightAccessor level) {
        return level.getMinSection();
    }

    static int sections(LevelHeightAccessor level) {
        return level.getSectionsCount();
    }
}
