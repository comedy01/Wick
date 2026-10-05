package dev.wick.core;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public final class Lights {
    public static final int MAX_PACKED = 240;

    private static volatile Snapshot current = Snapshot.EMPTY;

    public static volatile boolean vertexLight;

    private Lights() {
    }

    public static Snapshot current() {
        return current;
    }

    public static void publish(Snapshot snapshot) {
        current = snapshot;
    }

    public static int packedAt(int x, int y, int z) {
        return current.packedAt(x, y, z);
    }

    public static int falloff(double level, double distance) {
        double light = level - distance;
        if (light <= 0.0) {
            return 0;
        }
        return Math.min(MAX_PACKED, (int) (light * 16.0));
    }

    public static int reachRadius(int level) {
        return level + 1;
    }

    public static int reachIndex(int radius, int dx, int dy, int dz) {
        if (dx < -radius || dx > radius || dy < -radius || dy > radius || dz < -radius || dz > radius) {
            return -1;
        }
        int side = 2 * radius + 1;
        return ((dx + radius) * side + (dy + radius)) * side + (dz + radius);
    }

    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(new int[0], new int[0], new int[0], new int[0], 0);

        private static final int GRID_MIN = 8;
        private static final int[] NONE = new int[0];

        private final int[] xs;
        private final int[] ys;
        private final int[] zs;
        private final int[] levels;
        private final byte[][] paths;
        private final int count;
        private final int[] all;
        private final Long2ObjectMap<int[]> grid;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        public Snapshot(int[] xs, int[] ys, int[] zs, int[] levels, int count) {
            this(xs, ys, zs, levels, null, count);
        }

        public Snapshot(int[] xs, int[] ys, int[] zs, int[] levels, byte[][] paths, int count) {
            this.xs = xs;
            this.ys = ys;
            this.zs = zs;
            this.levels = levels;
            this.paths = paths;
            this.count = count;
            int loX = Integer.MAX_VALUE;
            int loY = Integer.MAX_VALUE;
            int loZ = Integer.MAX_VALUE;
            int hiX = Integer.MIN_VALUE;
            int hiY = Integer.MIN_VALUE;
            int hiZ = Integer.MIN_VALUE;
            for (int i = 0; i < count; i++) {
                int r = levels[i];
                loX = Math.min(loX, xs[i] - r);
                loY = Math.min(loY, ys[i] - r);
                loZ = Math.min(loZ, zs[i] - r);
                hiX = Math.max(hiX, xs[i] + r);
                hiY = Math.max(hiY, ys[i] + r);
                hiZ = Math.max(hiZ, zs[i] + r);
            }
            minX = loX;
            minY = loY;
            minZ = loZ;
            maxX = hiX;
            maxY = hiY;
            maxZ = hiZ;
            all = new int[count];
            for (int i = 0; i < count; i++) {
                all[i] = i;
            }
            grid = count >= GRID_MIN ? buildGrid() : null;
        }

        private Long2ObjectMap<int[]> buildGrid() {
            Long2ObjectOpenHashMap<IntArrayList> cells = new Long2ObjectOpenHashMap<>();
            for (int i = 0; i < count; i++) {
                int r = levels[i];
                for (int cx = (xs[i] - r) >> 4; cx <= (xs[i] + r) >> 4; cx++) {
                    for (int cy = (ys[i] - r) >> 4; cy <= (ys[i] + r) >> 4; cy++) {
                        for (int cz = (zs[i] - r) >> 4; cz <= (zs[i] + r) >> 4; cz++) {
                            cells.computeIfAbsent(cell(cx, cy, cz), key -> new IntArrayList(4)).add(i);
                        }
                    }
                }
            }
            Long2ObjectOpenHashMap<int[]> built = new Long2ObjectOpenHashMap<>(cells.size());
            for (Long2ObjectMap.Entry<IntArrayList> entry : cells.long2ObjectEntrySet()) {
                built.put(entry.getLongKey(), entry.getValue().toIntArray());
            }
            return built;
        }

        private static long cell(int cx, int cy, int cz) {
            return ((long) (cx & 0x3FFFFF) << 42) | ((long) (cy & 0xFFFFF) << 22) | (cz & 0x3FFFFF);
        }

        private int[] near(int x, int y, int z) {
            if (grid == null) {
                return all;
            }
            int[] found = grid.get(cell(x >> 4, y >> 4, z >> 4));
            return found == null ? NONE : found;
        }

        public int count() {
            return count;
        }

        private double travelled(int i, double straight, int bx, int by, int bz) {
            byte[] path = paths == null ? null : paths[i];
            if (path == null) {
                return straight;
            }
            int dx = bx - xs[i];
            int dy = by - ys[i];
            int dz = bz - zs[i];
            int index = reachIndex(reachRadius(levels[i]), dx, dy, dz);
            if (index < 0) {
                return -1.0;
            }
            int units = path[index] & 0xFF;
            if (units == Paths.UNREACHED) {
                return -1.0;
            }
            double detour = units / (Paths.SCALE * Paths.STRETCH) - Math.sqrt(dx * dx + dy * dy + dz * dz);
            return detour > 0.0 ? straight + detour : straight;
        }

        public int packedAtPoint(double x, double y, double z, int bx, int by, int bz) {
            if (count == 0 || x < minX || x > maxX + 1 || y < minY || y > maxY + 1 || z < minZ || z > maxZ + 1) {
                return 0;
            }
            int best = 0;
            for (int i : near((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))) {
                int level = levels[i];
                double dx = x - (xs[i] + 0.5);
                double dy = y - (ys[i] + 0.5);
                double dz = z - (zs[i] + 0.5);
                double squared = dx * dx + dy * dy + dz * dz;
                if (squared >= (double) level * level) {
                    continue;
                }
                double distance = travelled(i, Math.sqrt(squared), bx, by, bz);
                if (distance < 0.0) {
                    continue;
                }
                int light = falloff(level, distance);
                if (light > best) {
                    best = light;
                    if (best >= MAX_PACKED) {
                        break;
                    }
                }
            }
            return best;
        }

        public int packedAt(int x, int y, int z) {
            if (count == 0 || x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                return 0;
            }
            int best = 0;
            for (int i : near(x, y, z)) {
                int level = levels[i];
                int dx = x - xs[i];
                int dy = y - ys[i];
                int dz = z - zs[i];
                int squared = dx * dx + dy * dy + dz * dz;
                if (squared >= level * level) {
                    continue;
                }
                double distance = travelled(i, Math.sqrt(squared), x, y, z);
                if (distance < 0.0) {
                    continue;
                }
                int light = falloff(level, distance);
                if (light > best) {
                    best = light;
                    if (best >= MAX_PACKED) {
                        break;
                    }
                }
            }
            return best;
        }
    }
}
