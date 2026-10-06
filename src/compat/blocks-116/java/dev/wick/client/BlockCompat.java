package dev.wick.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class BlockCompat {
    private BlockCompat() {
    }

    public static boolean solid(BlockState state) {
        return state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    static int minSection(Level level) {
        return 0;
    }

    static int sections(Level level) {
        return 16;
    }

    static LevelChunkSection section(LevelChunk chunk, int index) {
        LevelChunkSection section = chunk.getSections()[index];
        return section == null || section.isEmpty() ? null : section;
    }
}
