package dev.wick.client;

import dev.wick.config.WickConfig;
import dev.wick.core.Lights;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class LightTracker {
    private static final Int2ObjectMap<Placed> placed = new Int2ObjectOpenHashMap<>();
    private static final LongOpenHashSet dirty = new LongOpenHashSet();
    static final int REBUILD_COOLDOWN = 4;
    private static final Long2IntOpenHashMap lastRebuild = new Long2IntOpenHashMap();
    private static final LongOpenHashSet waiting = new LongOpenHashSet();
    private static int clock;
    private static final LongOpenHashSet changedBlocks = new LongOpenHashSet();
    private static final LongOpenHashSet changedChunks = new LongOpenHashSet();

    private static ClientLevel level;
    private static int ticks;
    private static boolean lastWalls;
    private static int lastDirtied;
    private static long totalDirtied;
    private static long floods;
    private static long floodNanos;
    private static long worstFloodNanos;
    private static long updates;
    private static long updateNanos;
    private static long worstUpdateNanos;

    private record Placed(long pos, int level, byte[] reach) {
        boolean sameAs(Placed other) {
            return other != null && pos == other.pos && level == other.level && Arrays.equals(reach, other.reach);
        }
    }

    private record Candidate(int id, BlockPos pos, int level, double distance) {
    }

    private LightTracker() {
    }

    public static void tick(Minecraft mc) {
        handleKeys(mc);
        if (mc.level != level) {
            placed.clear();
            changedBlocks.clear();
            changedChunks.clear();
            Lights.publish(Lights.Snapshot.EMPTY);
            lastRebuild.clear();
            waiting.clear();
            level = mc.level;
        }
        if (level == null || mc.player == null) {
            return;
        }
        clock++;
        rebuildWaiting(mc);
        DataLights.update(mc, level);
        WickConfig config = WickClient.config();
        boolean off = !config.enabled();
        if (!off && ++ticks % config.updates().interval() != 0) {
            return;
        }
        update(mc, config, off ? List.of() : gather(mc, config));
    }

    public static void refresh(Minecraft mc) {
        if (mc.level != null && mc.level == level) {
            WickConfig config = WickClient.config();
            update(mc, config, config.enabled() ? gather(mc, config) : List.of());
        }
    }

    public static void blockChanged(BlockPos pos, BlockState before, BlockState after) {
        if (!placed.isEmpty() && lastWalls && BlockCompat.solid(before) != BlockCompat.solid(after)) {
            changedBlocks.add(pos.asLong());
        }
    }

    public static void chunkLoaded(int x, int z) {
        if (!placed.isEmpty() && lastWalls) {
            changedChunks.add((long) x << 32 | (z & 0xFFFFFFFFL));
        }
    }

    private static List<Candidate> gather(Minecraft mc, WickConfig config) {
        Entity camera = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
        Vec3 center = camera.position();
        double reach = (double) config.range() * config.range();
        List<Candidate> found = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (Vanilla.removed(entity)) {
                continue;
            }
            double distance = entity.position().distanceToSqr(center);
            if (distance > reach) {
                continue;
            }
            int light = EntityLights.level(entity, mc.player, config);
            if (light > 0) {
                found.add(new Candidate(entity.getId(), lightPos(entity), light, distance));
            }
        }
        if (found.size() > config.maxSources()) {
            found.sort(Comparator.comparingDouble(Candidate::distance));
            found = found.subList(0, config.maxSources());
        }
        return found;
    }

    private static BlockPos lightPos(Entity entity) {
        if (entity instanceof LivingEntity) {
            return Vanilla.blockPos(entity.getEyePosition(1.0F));
        }
        AABB box = entity.getBoundingBox();
        return Vanilla.blockPos(box.getCenter());
    }

    private static void update(Minecraft mc, WickConfig config, List<Candidate> sources) {
        long started = System.nanoTime();
        boolean walls = config.enabled() && config.wallsBlockLight();
        boolean wallsToggled = walls != lastWalls;
        lastWalls = walls;

        int count = sources.size();
        int[] xs = new int[count];
        int[] ys = new int[count];
        int[] zs = new int[count];
        int[] levels = new int[count];
        byte[][] reaches = walls ? new byte[count][] : null;
        Int2ObjectMap<Placed> next = new Int2ObjectOpenHashMap<>(count);
        for (int i = 0; i < count; i++) {
            Candidate source = sources.get(i);
            BlockPos pos = source.pos();
            xs[i] = pos.getX();
            ys[i] = pos.getY();
            zs[i] = pos.getZ();
            levels[i] = source.level();
            long packed = pos.asLong();
            byte[] reach = null;
            if (walls) {
                Placed before = placed.get(source.id());
                if (!wallsToggled && before != null && before.reach() != null && before.pos() == packed
                        && before.level() == source.level() && !touched(before)) {
                    reach = before.reach();
                } else {
                    long floodStart = System.nanoTime();
                    reach = Reach.flood(level, xs[i], ys[i], zs[i], levels[i]);
                    long took = System.nanoTime() - floodStart;
                    floods++;
                    floodNanos += took;
                    worstFloodNanos = Math.max(worstFloodNanos, took);
                }
                reaches[i] = reach;
            }
            next.put(source.id(), new Placed(packed, source.level(), reach));
        }
        changedBlocks.clear();
        changedChunks.clear();

        dirty.clear();
        for (Int2ObjectMap.Entry<Placed> entry : next.int2ObjectEntrySet()) {
            Placed before = placed.get(entry.getIntKey());
            Placed now = entry.getValue();
            if (!now.sameAs(before)) {
                if (before != null) {
                    reach(before);
                }
                reach(now);
            }
        }
        for (Int2ObjectMap.Entry<Placed> entry : placed.int2ObjectEntrySet()) {
            if (!next.containsKey(entry.getIntKey())) {
                reach(entry.getValue());
            }
        }
        placed.clear();
        placed.putAll(next);

        Lights.publish(count == 0 ? Lights.Snapshot.EMPTY : new Lights.Snapshot(xs, ys, zs, levels, reaches, count));
        lastDirtied = dirty.size();
        for (LongIterator it = dirty.iterator(); it.hasNext(); ) {
            long section = it.nextLong();
            if (lastRebuild.containsKey(section) && clock - lastRebuild.get(section) < REBUILD_COOLDOWN) {
                waiting.add(section);
            } else {
                rebuild(mc, section);
            }
        }
        long took = System.nanoTime() - started;
        updates++;
        updateNanos += took;
        worstUpdateNanos = Math.max(worstUpdateNanos, took);
    }

    private static void rebuild(Minecraft mc, long section) {
        waiting.remove(section);
        lastRebuild.put(section, clock);
        totalDirtied++;
        Sections.dirty(mc, SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
    }

    private static void rebuildWaiting(Minecraft mc) {
        if (!waiting.isEmpty()) {
            long[] ready = waiting.toLongArray();
            for (long section : ready) {
                if (clock - lastRebuild.get(section) >= REBUILD_COOLDOWN) {
                    rebuild(mc, section);
                }
            }
        }
        if (lastRebuild.size() > 256) {
            lastRebuild.long2IntEntrySet().removeIf(entry -> clock - entry.getIntValue() >= REBUILD_COOLDOWN);
        }
    }

    public static int waitingRebuilds() {
        return waiting.size();
    }

    private static boolean touched(Placed light) {
        if (changedBlocks.isEmpty() && changedChunks.isEmpty()) {
            return false;
        }
        int x = BlockPos.getX(light.pos());
        int y = BlockPos.getY(light.pos());
        int z = BlockPos.getZ(light.pos());
        int radius = Lights.reachRadius(light.level());
        for (LongIterator it = changedBlocks.iterator(); it.hasNext(); ) {
            long block = it.nextLong();
            if (Math.abs(BlockPos.getX(block) - x) <= radius && Math.abs(BlockPos.getY(block) - y) <= radius
                    && Math.abs(BlockPos.getZ(block) - z) <= radius) {
                return true;
            }
        }
        for (LongIterator it = changedChunks.iterator(); it.hasNext(); ) {
            long chunk = it.nextLong();
            int cx = (int) (chunk >> 32);
            int cz = (int) chunk;
            if (cx >= SectionPos.blockToSectionCoord(x - radius) && cx <= SectionPos.blockToSectionCoord(x + radius)
                    && cz >= SectionPos.blockToSectionCoord(z - radius) && cz <= SectionPos.blockToSectionCoord(z + radius)) {
                return true;
            }
        }
        return false;
    }

    private static void reach(Placed light) {
        int x = BlockPos.getX(light.pos());
        int y = BlockPos.getY(light.pos());
        int z = BlockPos.getZ(light.pos());
        int radius = light.level() + 1;
        double cx = x + 0.5;
        double cy = y + 0.5;
        double cz = z + 0.5;
        for (int sx = SectionPos.blockToSectionCoord(x - radius); sx <= SectionPos.blockToSectionCoord(x + radius); sx++) {
            for (int sy = SectionPos.blockToSectionCoord(y - radius); sy <= SectionPos.blockToSectionCoord(y + radius); sy++) {
                for (int sz = SectionPos.blockToSectionCoord(z - radius); sz <= SectionPos.blockToSectionCoord(z + radius); sz++) {
                    double dx = gap(cx, sx);
                    double dy = gap(cy, sy);
                    double dz = gap(cz, sz);
                    if (dx * dx + dy * dy + dz * dz <= (double) radius * radius) {
                        dirty.add(SectionPos.asLong(sx, sy, sz));
                    }
                }
            }
        }
    }

    private static double gap(double point, int section) {
        double min = SectionPos.sectionToBlockCoord(section);
        double max = min + 16.0;
        return point < min ? min - point : point > max ? point - max : 0.0;
    }

    private static void handleKeys(Minecraft mc) {
        KeyMapping toggle = WickClient.toggleKey();
        while (toggle != null && toggle.consumeClick()) {
            WickConfig config = WickClient.config();
            config.setEnabled(!config.enabled());
            WickClient.saveConfig();
            if (mc.player != null) {
                Messages.overlay(mc.player, Texts.translatable(config.enabled() ? "wick.notice.on" : "wick.notice.off"));
            }
        }
    }

    public static int sources() {
        return Lights.current().count();
    }

    public static int lastDirtied() {
        return lastDirtied;
    }

    public static long totalDirtied() {
        return totalDirtied;
    }

    public static String timings() {
        return String.format(java.util.Locale.ROOT, "%d updates avg %.1f us worst %.1f us; %d floods avg %.1f us worst %.1f us",
                updates, updates == 0 ? 0.0 : updateNanos / 1000.0 / updates, worstUpdateNanos / 1000.0,
                floods, floods == 0 ? 0.0 : floodNanos / 1000.0 / floods, worstFloodNanos / 1000.0);
    }

    public static void resetTimings() {
        updates = 0;
        updateNanos = 0;
        worstUpdateNanos = 0;
        floodNanos = 0;
        worstFloodNanos = 0;
        floods = 0;
    }

    public static long floods() {
        return floods;
    }
}
