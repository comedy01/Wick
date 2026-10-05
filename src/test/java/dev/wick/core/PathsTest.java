package dev.wick.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathsTest {
    @Test
    void stretchCoversEveryOpenSpacePath() {
        double worst = 0.0;
        for (int level = 1; level <= 15; level++) {
            int radius = Lights.reachRadius(level);
            byte[] path = Paths.fill(level, (dx, dy, dz) -> false);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        double straight = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        int units = path[Lights.reachIndex(radius, dx, dy, dz)] & 0xFF;
                        if (straight == 0.0 || straight >= level || units == Paths.UNREACHED) {
                            continue;
                        }
                        worst = Math.max(worst, units / (Paths.SCALE * straight));
                    }
                }
            }
        }
        System.out.println("worst open-space stretch " + worst);
        assertTrue(worst <= Paths.STRETCH, "stretch " + worst);
        assertTrue(worst > Paths.STRETCH - 0.02, "STRETCH is looser than it needs to be: " + worst);
    }

    @Test
    void openSpaceMatchesIgnoringWalls() {
        Random random = new Random(3);
        for (int level = 1; level <= 15; level++) {
            byte[] path = Paths.fill(level, (dx, dy, dz) -> false);
            Lights.Snapshot walled = new Lights.Snapshot(
                    new int[] {5}, new int[] {-3}, new int[] {9}, new int[] {level}, new byte[][] {path}, 1);
            Lights.Snapshot free = new Lights.Snapshot(new int[] {5}, new int[] {-3}, new int[] {9}, new int[] {level}, 1);
            for (int x = 5 - 17; x <= 5 + 17; x++) {
                for (int y = -3 - 17; y <= -3 + 17; y++) {
                    for (int z = 9 - 17; z <= 9 + 17; z++) {
                        assertEquals(free.packedAt(x, y, z), walled.packedAt(x, y, z), "level " + level + " at " + x + " " + y + " " + z);
                        double px = x + random.nextDouble();
                        double py = y + random.nextDouble();
                        double pz = z + random.nextDouble();
                        assertEquals(free.packedAtPoint(px, py, pz, x, y, z), walled.packedAtPoint(px, py, pz, x, y, z));
                    }
                }
            }
        }
    }

    @Test
    void lightBendsAroundAWall() {
        int level = 14;
        Paths.Walls walls = (dx, dy, dz) -> dz == -2 && dx != 3 && dx != 4;
        byte[] path = Paths.fill(level, walls);
        Lights.Snapshot walled = new Lights.Snapshot(
                new int[] {0}, new int[] {0}, new int[] {0}, new int[] {level}, new byte[][] {path}, 1);
        Lights.Snapshot free = new Lights.Snapshot(new int[] {0}, new int[] {0}, new int[] {0}, new int[] {level}, 1);
        int behind = walled.packedAt(0, -1, -4);
        System.out.println("behind the wall " + behind / 16.0 + ", without walls " + free.packedAt(0, -1, -4) / 16.0);
        assertTrue(behind > 0, "no light came around the wall");
        assertTrue(behind < free.packedAt(0, -1, -4) - 16, "the way around cost nothing: " + behind / 16.0);
        assertEquals(free.packedAt(2, 0, -1), walled.packedAt(2, 0, -1));
        assertEquals(free.packedAt(-5, 3, 4), walled.packedAt(-5, 3, 4));
        assertEquals(0, walled.packedAt(0, 0, -2));
    }

    @Test
    void lightDoesNotSqueezeThroughEdgeGaps() {
        Paths.Walls walls = (dx, dy, dz) -> !(dx == 0 && dy == 0 && dz == 0) && !(dx == 1 && dy == 0 && dz == 1);
        byte[] path = Paths.fill(8, walls);
        assertEquals(Paths.UNREACHED, path[Lights.reachIndex(Lights.reachRadius(8), 1, 0, 1)] & 0xFF);
    }
}
