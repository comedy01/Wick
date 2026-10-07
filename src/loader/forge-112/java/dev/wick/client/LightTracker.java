package dev.wick.client;

import dev.wick.config.WickConfig;
import dev.wick.core.Lights;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class LightTracker {
    private static final Int2ObjectMap<Placed> placed = new Int2ObjectOpenHashMap<>();
    private static final LongOpenHashSet dirty = new LongOpenHashSet();
    static final int REBUILD_COOLDOWN = 4;
    private static final Long2IntOpenHashMap lastRebuild = new Long2IntOpenHashMap();
    private static final LongOpenHashSet waiting = new LongOpenHashSet();
    private static int clock;
    private static final LongOpenHashSet changedBlocks = new LongOpenHashSet();
    private static final LongOpenHashSet changedChunks = new LongOpenHashSet();

    private static WorldClient level;
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

    private static final class Placed {
        final long pos;
        final int level;
        final byte[] reach;

        Placed(long pos, int level, byte[] reach) {
            this.pos = pos;
            this.level = level;
            this.reach = reach;
        }

        boolean sameAs(Placed other) {
            return other != null && pos == other.pos && level == other.level && Arrays.equals(reach, other.reach);
        }
    }

    private static final class Candidate {
        final int id;
        final BlockPos pos;
        final int level;
        final double distance;

        Candidate(int id, BlockPos pos, int level, double distance) {
            this.id = id;
            this.pos = pos;
            this.level = level;
            this.distance = distance;
        }
    }

    private LightTracker() {
    }

    public static void tick(Minecraft mc) {
        handleKeys(mc);
        if (mc.world != level) {
            placed.clear();
            changedBlocks.clear();
            changedChunks.clear();
            Lights.publish(Lights.Snapshot.EMPTY);
            lastRebuild.clear();
            waiting.clear();
            level = mc.world;
        }
        if (level == null || mc.player == null) {
            return;
        }
        clock++;
        rebuildWaiting(mc);
        WickConfig config = WickClient.config();
        boolean off = !config.enabled();
        if (!off && ++ticks % config.updates().interval() != 0) {
            return;
        }
        update(mc, config, off ? Collections.<Candidate>emptyList() : gather(mc, config));
    }

    public static void refresh(Minecraft mc) {
        if (mc.world != null && mc.world == level) {
            WickConfig config = WickClient.config();
            update(mc, config, config.enabled() ? gather(mc, config) : Collections.<Candidate>emptyList());
        }
    }

    public static void blockChanged(BlockPos pos, IBlockState before, IBlockState after) {
        if (!placed.isEmpty() && lastWalls && before.isOpaqueCube() != after.isOpaqueCube()) {
            changedBlocks.add(pos.toLong());
        }
    }

    public static void chunkLoaded(int x, int z) {
        if (!placed.isEmpty() && lastWalls) {
            changedChunks.add((long) x << 32 | (z & 0xFFFFFFFFL));
        }
    }

    private static List<Candidate> gather(Minecraft mc, WickConfig config) {
        Entity camera = mc.getRenderViewEntity() == null ? mc.player : mc.getRenderViewEntity();
        double reach = (double) config.range() * config.range();
        List<Candidate> found = new ArrayList<>();
        for (Entity entity : new ArrayList<>(level.loadedEntityList)) {
            if (entity.isDead) {
                continue;
            }
            double distance = entity.getDistanceSq(camera.posX, camera.posY, camera.posZ);
            if (distance > reach) {
                continue;
            }
            int light = EntityLights.level(entity, mc.player, config);
            if (light > 0) {
                found.add(new Candidate(entity.getEntityId(), lightPos(entity), light, distance));
            }
        }
        if (found.size() > config.maxSources()) {
            found.sort(Comparator.comparingDouble(candidate -> candidate.distance));
            found = found.subList(0, config.maxSources());
        }
        return found;
    }

    private static BlockPos lightPos(Entity entity) {
        if (entity instanceof EntityLivingBase) {
            return new BlockPos(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
        }
        AxisAlignedBB box = entity.getEntityBoundingBox();
        return new BlockPos((box.minX + box.maxX) / 2.0, (box.minY + box.maxY) / 2.0, (box.minZ + box.maxZ) / 2.0);
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
            BlockPos pos = source.pos;
            xs[i] = pos.getX();
            ys[i] = pos.getY();
            zs[i] = pos.getZ();
            levels[i] = source.level;
            long packed = pos.toLong();
            byte[] reach = null;
            if (walls) {
                Placed before = placed.get(source.id);
                if (!wallsToggled && before != null && before.reach != null && before.pos == packed
                        && before.level == source.level && !touched(before)) {
                    reach = before.reach;
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
            next.put(source.id, new Placed(packed, source.level, reach));
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

    private static long section(int x, int y, int z) {
        return ((long) x & 0x3FFFFF) << 42 | ((long) y & 0xFFFFF) << 22 | ((long) z & 0x3FFFFF);
    }

    private static int sectionX(long section) {
        return (int) (section >> 42);
    }

    private static int sectionY(long section) {
        return (int) (section << 22 >> 44);
    }

    private static int sectionZ(long section) {
        return (int) (section << 42 >> 42);
    }

    private static void rebuild(Minecraft mc, long section) {
        waiting.remove(section);
        lastRebuild.put(section, clock);
        totalDirtied++;
        int x = sectionX(section) << 4;
        int y = sectionY(section) << 4;
        int z = sectionZ(section) << 4;
        mc.renderGlobal.markBlockRangeForRenderUpdate(x + 1, y + 1, z + 1, x + 14, y + 14, z + 14);
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
        BlockPos pos = BlockPos.fromLong(light.pos);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int radius = Lights.reachRadius(light.level);
        for (LongIterator it = changedBlocks.iterator(); it.hasNext(); ) {
            BlockPos block = BlockPos.fromLong(it.nextLong());
            if (Math.abs(block.getX() - x) <= radius && Math.abs(block.getY() - y) <= radius
                    && Math.abs(block.getZ() - z) <= radius) {
                return true;
            }
        }
        for (LongIterator it = changedChunks.iterator(); it.hasNext(); ) {
            long chunk = it.nextLong();
            int cx = (int) (chunk >> 32);
            int cz = (int) chunk;
            if (cx >= (x - radius) >> 4 && cx <= (x + radius) >> 4 && cz >= (z - radius) >> 4 && cz <= (z + radius) >> 4) {
                return true;
            }
        }
        return false;
    }

    private static void reach(Placed light) {
        BlockPos pos = BlockPos.fromLong(light.pos);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int radius = light.level + 1;
        double cx = x + 0.5;
        double cy = y + 0.5;
        double cz = z + 0.5;
        int minY = Math.max(0, (y - radius) >> 4);
        int maxY = Math.min(15, (y + radius) >> 4);
        for (int sx = (x - radius) >> 4; sx <= (x + radius) >> 4; sx++) {
            for (int sy = minY; sy <= maxY; sy++) {
                for (int sz = (z - radius) >> 4; sz <= (z + radius) >> 4; sz++) {
                    double dx = gap(cx, sx);
                    double dy = gap(cy, sy);
                    double dz = gap(cz, sz);
                    if (dx * dx + dy * dy + dz * dz <= (double) radius * radius) {
                        dirty.add(section(sx, sy, sz));
                    }
                }
            }
        }
    }

    private static double gap(double point, int section) {
        double min = section << 4;
        double max = min + 16.0;
        return point < min ? min - point : point > max ? point - max : 0.0;
    }

    private static void handleKeys(Minecraft mc) {
        while (WickClient.toggleKey() != null && WickClient.toggleKey().isPressed()) {
            WickConfig config = WickClient.config();
            config.setEnabled(!config.enabled());
            WickClient.saveConfig();
            if (mc.player != null) {
                mc.ingameGUI.setOverlayMessage(I18n.format(config.enabled() ? "wick.notice.on" : "wick.notice.off"), false);
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
        return String.format(Locale.ROOT, "%d updates avg %.1f us worst %.1f us; %d floods avg %.1f us worst %.1f us",
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
