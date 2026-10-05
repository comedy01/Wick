package dev.wick.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightsTest {
    @Test
    void falloffMatchesVanillaAtTheSource() {
        assertEquals(14 * 16, Lights.falloff(14, 0.0));
        assertEquals(15 * 16, Lights.falloff(15, 0.0));
        assertEquals(13 * 16, Lights.falloff(14, 1.0));
    }

    @Test
    void falloffKeepsFractionsAndEndsAtZero() {
        assertEquals((int) ((14 - Math.sqrt(2)) * 16), Lights.falloff(14, Math.sqrt(2)));
        assertEquals(0, Lights.falloff(14, 14.0));
        assertEquals(0, Lights.falloff(14, 20.0));
    }

    @Test
    void brightestSourceWins() {
        Lights.Snapshot snapshot = new Lights.Snapshot(
                new int[] {0, 10}, new int[] {0, 0}, new int[] {0, 0}, new int[] {14, 8}, 2);
        assertEquals(14 * 16, snapshot.packedAt(0, 0, 0));
        assertEquals(8 * 16, snapshot.packedAt(10, 0, 0));
        assertEquals(9 * 16, snapshot.packedAt(5, 0, 0));
        assertEquals(0, snapshot.packedAt(40, 0, 0));
    }

    @Test
    void emptySnapshotIsDark() {
        assertEquals(0, Lights.Snapshot.EMPTY.packedAt(0, 0, 0));
    }

    @Test
    void dynamicLightOnlyRaisesBlockLight() {
        int coords = (15 << 20) | (4 << 4);
        assertEquals((15 << 20) | 200, Packed.withDynamic(coords, 200));
        assertEquals(coords, Packed.withDynamic(coords, 32));
    }

    @Test
    void gridLookupMatchesCheckingEverySource() {
        Random random = new Random(7);
        int count = 60;
        int[] xs = new int[count];
        int[] ys = new int[count];
        int[] zs = new int[count];
        int[] levels = new int[count];
        for (int i = 0; i < count; i++) {
            xs[i] = random.nextInt(120) - 60;
            ys[i] = random.nextInt(40) - 20;
            zs[i] = random.nextInt(120) - 60;
            levels[i] = 1 + random.nextInt(15);
        }
        Lights.Snapshot gridded = new Lights.Snapshot(xs, ys, zs, levels, count);
        for (int n = 0; n < 20000; n++) {
            int x = random.nextInt(160) - 80;
            int y = random.nextInt(60) - 30;
            int z = random.nextInt(160) - 80;
            int expected = 0;
            for (int i = 0; i < count; i++) {
                Lights.Snapshot single = new Lights.Snapshot(
                        new int[] {xs[i]}, new int[] {ys[i]}, new int[] {zs[i]}, new int[] {levels[i]}, 1);
                expected = Math.max(expected, single.packedAt(x, y, z));
            }
            assertEquals(expected, gridded.packedAt(x, y, z), "at " + x + " " + y + " " + z);
            double px = x + random.nextDouble();
            double py = y + random.nextDouble();
            double pz = z + random.nextDouble();
            int expectedPoint = 0;
            for (int i = 0; i < count; i++) {
                Lights.Snapshot single = new Lights.Snapshot(
                        new int[] {xs[i]}, new int[] {ys[i]}, new int[] {zs[i]}, new int[] {levels[i]}, 1);
                expectedPoint = Math.max(expectedPoint, single.packedAtPoint(px, py, pz, x, y, z));
            }
            assertEquals(expectedPoint, gridded.packedAtPoint(px, py, pz, x, y, z));
        }
    }

    @Test
    void lightStopsWhereItsReachEnds() {
        int level = 10;
        byte[] path = Paths.fill(level, (dx, dy, dz) -> dx >= 3);
        Lights.Snapshot snapshot = new Lights.Snapshot(
                new int[] {0}, new int[] {0}, new int[] {0}, new int[] {level}, new byte[][] {path}, 1);
        assertEquals(8 * 16, snapshot.packedAt(2, 0, 0));
        assertEquals(0, snapshot.packedAt(4, 0, 0));
        assertEquals(10 * 16, snapshot.packedAt(0, 0, 0));
        assertEquals(0, snapshot.packedAtPoint(4.0, 0.5, 0.5, 4, 0, 0));
        assertEquals((int) ((10 - 2.5) * 16), snapshot.packedAtPoint(3.0, 0.5, 0.5, 2, 0, 0));
    }

    @Test
    void reachIndexIsOutsideBeyondTheRadius() {
        assertEquals(-1, Lights.reachIndex(4, 5, 0, 0));
        assertEquals(0, Lights.reachIndex(4, -4, -4, -4));
        assertEquals(9 * 9 * 9 - 1, Lights.reachIndex(4, 4, 4, 4));
    }
}
