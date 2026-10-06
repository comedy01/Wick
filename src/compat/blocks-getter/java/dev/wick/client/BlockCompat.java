package dev.wick.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

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

    static LevelChunkSection section(LevelChunk chunk, int index) {
        LevelChunkSection section = chunk.getSection(index);
        return section.hasOnlyAir() ? null : section;
    }
}
