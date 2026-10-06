package dev.wick.client;

import dev.wick.core.Paths;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

final class Reach {
    private Reach() {
    }

    static byte[] flood(ClientLevel level, int sx, int sy, int sz, int light) {
        Blocks blocks = new Blocks(level);
        return Paths.fill(light, (dx, dy, dz) -> blocks.opaque(sx + dx, sy + dy, sz + dz));
    }

    private static final class Blocks {
        private final ClientLevel level;
        private final int minSection;
        private final int sections;
        private long lastKey = Long.MIN_VALUE;
        private LevelChunkSection last;

        Blocks(ClientLevel level) {
            this.level = level;
            this.minSection = BlockCompat.minSection(level);
            this.sections = BlockCompat.sections(level);
        }

        boolean opaque(int x, int y, int z) {
            int cx = x >> 4;
            int cy = y >> 4;
            int cz = z >> 4;
            long key = ((long) cx & 0x3FFFFF) << 42 | ((long) cy & 0xFFFFF) << 22 | ((long) cz & 0x3FFFFF);
            if (key != lastKey) {
                lastKey = key;
                last = null;
                int index = cy - minSection;
                if (index >= 0 && index < sections) {
                    LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                    if (chunk != null) {
                        last = BlockCompat.section(chunk, index);
                    }
                }
            }
            LevelChunkSection section = last;
            return section != null && BlockCompat.solid(section.getBlockState(x & 15, y & 15, z & 15));
        }
    }
}
