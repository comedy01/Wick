package dev.wick.core;

import java.util.Arrays;

public final class Paths {
    public static final int SCALE = 8;
    public static final int UNREACHED = 0xFF;
    public static final double STRETCH = 1.135;

    @FunctionalInterface
    public interface Walls {
        boolean opaque(int dx, int dy, int dz);
    }

    private static final int[] STEP_X = new int[26];
    private static final int[] STEP_Y = new int[26];
    private static final int[] STEP_Z = new int[26];
    private static final int[] STEP_COST = new int[26];

    static {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (axes == 0) {
                        continue;
                    }
                    STEP_X[n] = dx;
                    STEP_Y[n] = dy;
                    STEP_Z[n] = dz;
                    STEP_COST[n] = axes == 1 ? 8 : axes == 2 ? 11 : 14;
                    n++;
                }
            }
        }
    }

    private static int[][] buckets = new int[0][];
    private static int[] bucketSizes = new int[0];
    private static byte[] state = new byte[0];

    private Paths() {
    }

    public static int maxUnits(int level) {
        return Math.min(UNREACHED - 1, (int) Math.ceil((level + 1) * SCALE * STRETCH));
    }

    public static byte[] fill(int level, Walls walls) {
        int radius = Lights.reachRadius(level);
        int side = 2 * radius + 1;
        int volume = side * side * side;
        int limit = radius * radius;
        int max = maxUnits(level);
        byte[] dist = new byte[volume];
        Arrays.fill(dist, (byte) UNREACHED);
        if (state.length < volume) {
            state = new byte[volume];
        } else {
            Arrays.fill(state, 0, volume, (byte) 0);
        }
        if (buckets.length < max + 1) {
            buckets = Arrays.copyOf(buckets, max + 1);
            bucketSizes = Arrays.copyOf(bucketSizes, max + 1);
        }
        for (int i = 0; i <= max; i++) {
            if (buckets[i] == null) {
                buckets[i] = new int[64];
            }
            bucketSizes[i] = 0;
        }

        int strideX = side * side;
        int[] delta = new int[26];
        for (int s = 0; s < 26; s++) {
            delta[s] = STEP_X[s] * strideX + STEP_Y[s] * side + STEP_Z[s];
        }

        int start = Lights.reachIndex(radius, 0, 0, 0);
        dist[start] = 0;
        push(0, start);
        for (int d = 0; d <= max; d++) {
            for (int k = 0; k < bucketSizes[d]; k++) {
                int index = buckets[d][k];
                if ((dist[index] & 0xFF) != d) {
                    continue;
                }
                int dz = index % side - radius;
                int dy = (index / side) % side - radius;
                int dx = index / strideX - radius;
                for (int s = 0; s < 26; s++) {
                    int next = d + STEP_COST[s];
                    int target = index + delta[s];
                    if (next > max || (dist[target] & 0xFF) <= next) {
                        continue;
                    }
                    int sx = STEP_X[s];
                    int sy = STEP_Y[s];
                    int sz = STEP_Z[s];
                    int nx = dx + sx;
                    int ny = dy + sy;
                    int nz = dz + sz;
                    if (nx * nx + ny * ny + nz * nz >= limit || !open(walls, target, nx, ny, nz)) {
                        continue;
                    }
                    if (STEP_COST[s] > 8 && !(
                            (sx == 0 || open(walls, index + sx * strideX, dx + sx, dy, dz))
                                    && (sy == 0 || open(walls, index + sy * side, dx, dy + sy, dz))
                                    && (sz == 0 || open(walls, index + sz, dx, dy, dz + sz)))) {
                        continue;
                    }
                    dist[target] = (byte) next;
                    push(next, target);
                }
            }
        }
        return dist;
    }

    private static boolean open(Walls walls, int index, int dx, int dy, int dz) {
        byte known = state[index];
        if (known == 0) {
            known = walls.opaque(dx, dy, dz) ? (byte) 2 : (byte) 1;
            state[index] = known;
        }
        return known == 1;
    }

    private static void push(int bucket, int index) {
        int size = bucketSizes[bucket];
        if (size == buckets[bucket].length) {
            buckets[bucket] = Arrays.copyOf(buckets[bucket], size * 2);
        }
        buckets[bucket][size] = index;
        bucketSizes[bucket] = size + 1;
    }
}
