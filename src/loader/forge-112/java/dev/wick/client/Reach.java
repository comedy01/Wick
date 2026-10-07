package dev.wick.client;

import dev.wick.core.Paths;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

final class Reach {
    private Reach() {
    }

    static byte[] flood(WorldClient level, int sx, int sy, int sz, int light) {
        Blocks blocks = new Blocks(level);
        return Paths.fill(light, (dx, dy, dz) -> blocks.opaque(sx + dx, sy + dy, sz + dz));
    }

    private static final class Blocks {
        private final WorldClient level;
        private long lastKey = Long.MIN_VALUE;
        private ExtendedBlockStorage last;

        Blocks(WorldClient level) {
            this.level = level;
        }

        boolean opaque(int x, int y, int z) {
            int cx = x >> 4;
            int cy = y >> 4;
            int cz = z >> 4;
            long key = ((long) cx & 0x3FFFFF) << 42 | ((long) cy & 0xFFFFF) << 22 | ((long) cz & 0x3FFFFF);
            if (key != lastKey) {
                lastKey = key;
                last = null;
                if (cy >= 0 && cy < 16) {
                    Chunk chunk = level.getChunkProvider().getLoadedChunk(cx, cz);
                    if (chunk != null) {
                        last = chunk.getBlockStorageArray()[cy];
                    }
                }
            }
            ExtendedBlockStorage section = last;
            return section != null && section.get(x & 15, y & 15, z & 15).isOpaqueCube();
        }
    }
}
