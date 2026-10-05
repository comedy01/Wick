package dev.wick.showcase;

import com.mojang.datafixers.util.Pair;
import dev.wick.client.WickClient;
import dev.wick.client.gui.WickSettingsScreen;
import dev.wick.config.WickConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class Showcase {
    private static final String WORLD = "wick-showcase";
    private static final long SETTLE_NANOS = 3_000_000_000L;
    private static final int GLIDE = 24;
    private static final int EDGE = 28;
    private static final int TUNNEL = 48;
    private static final String[] ARROW = {
            "B",
            "BB",
            "BWB",
            "BWWB",
            "BWWWB",
            "BWWWWB",
            "BWWWWWB",
            "BWWWWWWB",
            "BWWWWWWWB",
            "BWWWWWWWWB",
            "BWWWWWWWWWB",
            "BWWWWWWBBBBB",
            "BWWWBWWB",
            "BWWB BWWB",
            "BWB  BWWB",
            "BB    BWWB",
            "B     BWWB",
            "       BWWB",
            "        BB"};
    private static Showcase instance;

    private final Path out;
    private final Recorder recorder;
    private final long seed;
    private final List<Action> actions = new ArrayList<>();
    private final AtomicReference<BlockPos> site = new AtomicReference<>();
    private BlockPos under;
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int frame;
    private long actionStart;
    private Path pendingStill;
    private boolean cursorShown;
    private boolean showHand;
    private double cursorX;
    private double cursorY;

    private interface Action {
        boolean run(int frame);
    }

    private record Key(int move, int hold, Vec3 target) {
    }

    private record Stop(int frame, Supplier<double[]> where) {
    }

    private record Walker(String tag, Vec3 from, double yaw, double speed) {
        Vec3 at(double ticks) {
            double r = Math.toRadians(yaw);
            return from.add(-Math.sin(r) * speed * ticks, 0, Math.cos(r) * speed * ticks);
        }
    }

    private Showcase(Path out, String ffmpeg, long seed) {
        this.out = out;
        this.recorder = new Recorder(ffmpeg);
        this.seed = seed;
    }

    public static void start() {
        String ffmpeg = System.getProperty("wick.ffmpeg", "ffmpeg");
        long seed = Long.parseLong(System.getProperty("wick.seed", "12345"));
        instance = new Showcase(Path.of(System.getProperty("wick.showcase")), ffmpeg, seed);
    }

    public static boolean hideHand() {
        return instance != null && instance.started && !instance.showHand;
    }

    public static boolean hideHud() {
        return instance != null && instance.started;
    }

    public static void frame() {
        if (instance != null) {
            instance.onFrame();
        }
    }

    public static void drawCursor(GuiGraphicsExtractor graphics) {
        if (instance == null || !instance.started || !instance.cursorShown) {
            return;
        }
        float cell = 2.0F / (float) Minecraft.getInstance().getWindow().getGuiScale();
        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) instance.cursorX, (float) instance.cursorY);
        graphics.pose().scale(cell, cell);
        for (int row = 0; row < ARROW.length; row++) {
            String line = ARROW[row];
            int x = 0;
            while (x < line.length()) {
                char c = line.charAt(x);
                int end = x;
                while (end < line.length() && line.charAt(end) == c) {
                    end++;
                }
                if (c != ' ') {
                    graphics.fill(x, row, end, row + 1, c == 'B' ? 0xFF000000 : 0xFFFFFFFF);
                }
                x = end;
            }
        }
        graphics.pose().popMatrix();
    }

    private void onFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 120) {
                started = true;
                setUpOptions(mc);
                plan(mc);
                log("creating world with seed " + seed);
                deleteWorld(mc);
                Worlds.create(mc, WORLD, false, seed);
            }
            return;
        }
        Clock.step();
        mc.gui.toastManager().clear();
        try {
            while (index < actions.size()) {
                if (frame == 0) {
                    actionStart = System.nanoTime();
                }
                if (!actions.get(index).run(frame++)) {
                    if (cursorShown && Screens.current(mc) != null) {
                        double scale = mc.getWindow().getGuiScale();
                        Ui.moveMouse(mc, cursorX * scale, cursorY * scale);
                    }
                    return;
                }
                index++;
                frame = 0;
            }
        } catch (Throwable e) {
            log("FAILED: " + e);
            e.printStackTrace();
        }
        finish(mc);
    }

    private void finish(Minecraft mc) {
        finished = true;
        Clock.release();
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(90_000L);
            } catch (InterruptedException e) {
                return;
            }
            log("the game did not close, forcing it");
            Runtime.getRuntime().halt(1);
        }, "wick-showcase-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        WickClient.config().resetToDefaults();
        WickClient.saveConfig();
        log("showcase done");
        mc.stop();
    }

    private static void setUpOptions(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.renderDistance().set(12);
        mc.options.bobView().set(false);
        mc.options.gamma().set(Double.parseDouble(System.getProperty("wick.gamma", "0.5")));
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.guiScale().set(3);
        mc.resizeGui();
    }

    private void once(Runnable action) {
        actions.add(f -> {
            action.run();
            return true;
        });
    }

    private void until(BooleanSupplier ready) {
        actions.add(f -> ready.getAsBoolean());
    }

    private void holdFor(int frames, Runnable hold) {
        actions.add(f -> {
            hold.run();
            return f >= frames;
        });
    }

    private void settle(Minecraft mc, Runnable hold) {
        actions.add(f -> {
            hold.run();
            return f >= 90 && System.nanoTime() - actionStart > SETTLE_NANOS
                    && (mc.levelRenderer.hasRenderedAllSections() || System.nanoTime() - actionStart > 20 * SETTLE_NANOS);
        });
    }

    private void record(String name, int frames, IntConsumer script) {
        actions.add(f -> {
            if (f == 0) {
                log("recording " + name);
                recorder.start(out.resolve("clips").resolve(name + ".mp4"));
            } else {
                Path still = pendingStill;
                pendingStill = null;
                recorder.capture(still);
            }
            if (f < frames) {
                script.accept(f);
                return false;
            }
            return true;
        });
        until(recorder::drained);
        once(recorder::stop);
    }

    private void shot(String name) {
        pendingStill = out.resolve("stills").resolve(name + ".png");
    }

    static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void cmd(Minecraft mc, String format, Object... args) {
        run(mc, String.format(Locale.ROOT, format, args));
    }

    private static void face(Minecraft mc, double yaw, double pitch) {
        LocalPlayer player = mc.player;
        player.setYRot((float) yaw);
        player.setXRot((float) pitch);
        player.yRotO = (float) yaw;
        player.xRotO = (float) pitch;
        player.setYHeadRot((float) yaw);
        player.yHeadRotO = (float) yaw;
        player.yBodyRot = (float) yaw;
        player.yBodyRotO = (float) yaw;
    }

    private static void lookAt(Minecraft mc, Vec3 target) {
        Vec3 eye = mc.player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        face(mc, Math.toDegrees(Math.atan2(-dx, dz)), -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private static void place(Minecraft mc, Vec3 feet) {
        LocalPlayer player = mc.player;
        player.getAbilities().flying = true;
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(feet.x, feet.y, feet.z);
        player.xo = feet.x;
        player.yo = feet.y;
        player.zo = feet.z;
        player.xOld = feet.x;
        player.yOld = feet.y;
        player.zOld = feet.z;
    }

    private static void follow(Minecraft mc, int f, Key... keys) {
        lookAt(mc, track(f, keys));
    }

    private static Vec3 track(int f, Key... keys) {
        Vec3 target = keys[0].target();
        int t = f - keys[0].hold();
        for (int i = 1; i < keys.length && t >= 0; i++) {
            Key key = keys[i];
            if (t < key.move()) {
                return lerp(keys[i - 1].target(), key.target(), smooth(t / (double) key.move()));
            }
            t -= key.move();
            target = key.target();
            t -= key.hold();
        }
        return target;
    }

    private void cursor(int f, Stop... stops) {
        double[] at = stops[0].where().get();
        for (int i = 1; i < stops.length; i++) {
            Stop stop = stops[i];
            if (f >= stop.frame()) {
                at = stop.where().get();
            } else if (f >= stop.frame() - GLIDE) {
                double[] to = stop.where().get();
                double s = smooth((f - (stop.frame() - GLIDE)) / (double) GLIDE);
                at = new double[] {lerp(at[0], to[0], s), lerp(at[1], to[1], s)};
                break;
            } else {
                break;
            }
        }
        cursorShown = true;
        cursorX = at[0];
        cursorY = at[1];
    }

    private static void select(Minecraft mc, int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
    }

    private static void swing(Minecraft mc) {
        mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false);
    }

    private static boolean wanted(String scene) {
        String only = System.getProperty("wick.scenes");
        return only == null || List.of(only.split(",")).contains(scene);
    }

    private Vec3 at(double dx, double dy, double dz) {
        BlockPos c = site.get();
        return new Vec3(c.getX() + dx + 0.5, c.getY() + dy, c.getZ() + dz + 0.5);
    }

    private Vec3 u(double dx, double dy, double dz) {
        return new Vec3(under.getX() + dx + 0.5, under.getY() + dy, under.getZ() + dz + 0.5);
    }

    private static int height(ServerLevel level, int x, int z) {
        return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
    }

    private void locate(Minecraft mc) {
        once(() -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerLevel level = server.overworld();
                ChunkGenerator generator = level.getChunkSource().getGenerator();
                RandomState noise = level.getChunkSource().randomState();
                Pair<BlockPos, Holder<Biome>> pair = level.findClosestBiome3d(h -> h.is(Biomes.PLAINS),
                        BlockPos.ZERO, 6400, 32, 64);
                BlockPos found = pair == null ? BlockPos.ZERO : pair.getFirst();
                BlockPos best = null;
                int bestScore = Integer.MAX_VALUE;
                for (int ox = -1536; ox <= 1536; ox += 96) {
                    for (int oz = -1536; oz <= 1536; oz += 96) {
                        int cx = found.getX() + ox;
                        int cz = found.getZ() + oz;
                        int centre = generator.getBaseHeight(cx, cz, Heightmap.Types.WORLD_SURFACE_WG, level, noise);
                        if (!level.getBiome(new BlockPos(cx, centre, cz)).is(Biomes.PLAINS)) {
                            continue;
                        }
                        List<Integer> heights = new ArrayList<>();
                        int wet = 0;
                        for (int dx = -40; dx <= 40; dx += 8) {
                            for (int dz = -40; dz <= 40; dz += 8) {
                                int top = generator.getBaseHeight(cx + dx, cz + dz, Heightmap.Types.WORLD_SURFACE_WG,
                                        level, noise);
                                int floor = generator.getBaseHeight(cx + dx, cz + dz, Heightmap.Types.OCEAN_FLOOR_WG,
                                        level, noise);
                                heights.add(top);
                                if (top != floor) {
                                    wet++;
                                }
                            }
                        }
                        int usual = mode(heights);
                        int score = wet * 3;
                        for (int h : heights) {
                            if (Math.abs(h - usual) > 1) {
                                score++;
                            }
                        }
                        if (score < bestScore) {
                            bestScore = score;
                            best = new BlockPos(cx, centre, cz);
                        }
                    }
                }
                site.set(best == null ? found : best);
                log("plains at " + site.get().toShortString() + ", " + bestScore + " uneven samples");
            });
        });
        until(() -> site.get() != null);
    }

    private static int mode(List<Integer> values) {
        Map<Integer, Integer> counts = new HashMap<>();
        int best = values.get(0);
        for (int value : values) {
            int count = counts.merge(value, 1, Integer::sum);
            if (count > counts.get(best)) {
                best = value;
            }
        }
        return best;
    }

    private void level(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        int ground = server.submit(() -> {
            ServerLevel level = server.overworld();
            BlockPos c = site.get();
            List<Integer> heights = new ArrayList<>();
            for (int dx = -EDGE; dx <= EDGE; dx += 2) {
                for (int dz = -EDGE; dz <= EDGE; dz += 2) {
                    heights.add(height(level, c.getX() + dx, c.getZ() + dz));
                }
            }
            return mode(heights);
        }).join();
        BlockPos c = site.get();
        site.set(new BlockPos(c.getX(), ground + 1, c.getZ()));
        under = new BlockPos(c.getX(), ground - 40, c.getZ());
        log("meadow at y " + (ground + 1) + ", tunnel at y " + under.getY());
    }

    private static void fill(Minecraft mc, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        int layers = Math.max(1, 32768 / ((Math.abs(x2 - x1) + 1) * (Math.abs(z2 - z1) + 1)));
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y += layers) {
            cmd(mc, "fill %d %d %d %d %d %d %s", x1, y, z1, x2, Math.min(y + layers - 1, Math.max(y1, y2)), z2, block);
        }
    }

    private void buildMeadow(Minecraft mc) {
        BlockPos c = site.get();
        int x = c.getX();
        int z = c.getZ();
        int ground = c.getY() - 1;
        fill(mc, x - EDGE, ground + 1, z - EDGE, x + EDGE, ground + 40, z + EDGE, "minecraft:air");
        fill(mc, x - EDGE, ground - 6, z - EDGE, x + EDGE, ground - 1, z + EDGE, "minecraft:dirt");
        fill(mc, x - EDGE, ground, z - EDGE, x + EDGE, ground, z + EDGE, "minecraft:grass_block");
        String[] kinds = {"oak", "birch", "fancy_oak"};
        int[][] trees = {{-9, -19}, {7, -21}, {-19, -15}, {18, -17}, {-3, -25}, {22, -24}, {-24, -23}, {13, -13},
                {-14, -12}, {25, -9}, {-26, -6}, {-12, 14}, {10, 16}, {-22, 11}, {21, 13}};
        for (int i = 0; i < trees.length; i++) {
            cmd(mc, "place feature minecraft:%s %d %d %d", kinds[i % kinds.length], x + trees[i][0], ground + 1,
                    z + trees[i][1]);
        }
        Random random = new Random(seed);
        String[] flowers = {"dandelion", "poppy", "oxeye_daisy", "cornflower", "azure_bluet", "lily_of_the_valley"};
        for (int dx = -EDGE + 1; dx <= EDGE - 1; dx++) {
            for (int dz = -EDGE + 1; dz <= EDGE - 1; dz++) {
                double roll = random.nextDouble();
                String block;
                if (roll < 0.16) {
                    block = "short_grass";
                } else if (roll < 0.20) {
                    block = flowers[random.nextInt(flowers.length)];
                } else if (roll < 0.21) {
                    block = "tall_grass";
                } else {
                    continue;
                }
                cmd(mc, "setblock %d %d %d minecraft:%s keep", x + dx, ground + 1, z + dz, block);
            }
        }
        run(mc, "kill @e[type=!minecraft:player]");
    }

    private record Palette(BlockState[] states, int[] weights, int total) {
        static Palette of(Object... pairs) {
            BlockState[] states = new BlockState[pairs.length / 2];
            int[] weights = new int[pairs.length / 2];
            int total = 0;
            for (int i = 0; i < states.length; i++) {
                states[i] = ((Block) pairs[i * 2]).defaultBlockState();
                weights[i] = (Integer) pairs[i * 2 + 1];
                total += weights[i];
            }
            return new Palette(states, weights, total);
        }

        BlockState pick(Random random) {
            int roll = random.nextInt(total);
            for (int i = 0; i < states.length; i++) {
                roll -= weights[i];
                if (roll < 0) {
                    return states[i];
                }
            }
            return states[0];
        }
    }

    private static final Palette MINE = Palette.of(Blocks.STONE, 44, Blocks.ANDESITE, 14, Blocks.COBBLESTONE, 8,
            Blocks.TUFF, 8, Blocks.GRAVEL, 4, Blocks.GRANITE, 3, Blocks.COAL_ORE, 8, Blocks.IRON_ORE, 6,
            Blocks.COPPER_ORE, 4, Blocks.GOLD_ORE, 1, Blocks.DIAMOND_ORE, 1);
    private static final Palette MINE_FLOOR = Palette.of(Blocks.GRAVEL, 25, Blocks.COARSE_DIRT, 20, Blocks.STONE, 25,
            Blocks.ANDESITE, 15, Blocks.COBBLESTONE, 15);
    private static final Palette NETHER = Palette.of(Blocks.NETHER_BRICKS, 60, Blocks.CRACKED_NETHER_BRICKS, 20,
            Blocks.CHISELED_NETHER_BRICKS, 4, Blocks.RED_NETHER_BRICKS, 10, Blocks.BLACKSTONE, 6);
    private static final Palette NETHER_FLOOR = Palette.of(Blocks.NETHERRACK, 35, Blocks.SOUL_SAND, 20,
            Blocks.NETHER_BRICKS, 25, Blocks.SOUL_SOIL, 20);
    private static final Palette VAULT = Palette.of(Blocks.STONE_BRICKS, 55, Blocks.MOSSY_STONE_BRICKS, 20,
            Blocks.CRACKED_STONE_BRICKS, 20, Blocks.CHISELED_STONE_BRICKS, 5);
    private static final Palette VAULT_FLOOR = Palette.of(Blocks.DEEPSLATE_TILES, 50, Blocks.POLISHED_DEEPSLATE, 35,
            Blocks.CRACKED_DEEPSLATE_TILES, 15);
    private static final Palette POOL_FLOOR = Palette.of(Blocks.SAND, 40, Blocks.GRAVEL, 30, Blocks.CLAY, 30);

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void room(ServerLevel level, Random random, int x1, int y1, int z1, int x2, int y2, int z2,
                             Palette walls, Palette floor) {
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int y = y1 - 1; y <= y2 + 1; y++) {
                for (int z = z1 - 1; z <= z2 + 1; z++) {
                    boolean inside = x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
                    set(level, x, y, z, inside ? Blocks.AIR.defaultBlockState()
                            : (y < y1 ? floor : walls).pick(random));
                }
            }
        }
    }

    private void buildUnderground(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.submit(() -> {
            ServerLevel level = server.overworld();
            Random random = new Random(seed + 1);
            int x = under.getX();
            int y = under.getY();
            int z = under.getZ();
            for (int bx = x - 30; bx <= x + 30; bx++) {
                for (int by = y - 3; by <= y + 12; by++) {
                    for (int bz = z - TUNNEL - 6; bz <= z + 8; bz++) {
                        set(level, bx, by, bz, MINE.pick(random));
                    }
                }
            }

            room(level, random, x - 2, y, z - TUNNEL, x + 2, y + 3, z, MINE, MINE_FLOOR);
            BlockState log = Blocks.OAK_LOG.defaultBlockState();
            BlockState beam = Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
            for (int s = z - 3; s >= z - TUNNEL + 2; s -= 6) {
                for (int h = 0; h <= 2; h++) {
                    set(level, x - 2, y + h, s, log);
                    set(level, x + 2, y + h, s, log);
                }
                for (int bx = x - 2; bx <= x + 2; bx++) {
                    set(level, bx, y + 3, s, beam);
                }
            }
            for (int bz = z; bz >= z - TUNNEL; bz--) {
                set(level, x, y, bz, Blocks.RAIL.defaultBlockState());
                for (int side : new int[] {x - 2, x + 2}) {
                    if (level.getBlockState(new BlockPos(side, y + 3, bz)).isAir() && random.nextDouble() < 0.14) {
                        set(level, side, y + 3, bz, Blocks.COBWEB.defaultBlockState());
                    }
                    if (level.getBlockState(new BlockPos(side, y, bz)).isAir() && random.nextDouble() < 0.04) {
                        set(level, side, y, bz, (random.nextBoolean() ? Blocks.COBBLESTONE : Blocks.GRAVEL)
                                .defaultBlockState());
                    }
                }
            }

            room(level, random, x + 10, y, z - 10, x + 22, y + 6, z + 2, NETHER, NETHER_FLOOR);
            BlockState wart = Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 3);
            for (int bx = x + 10; bx <= x + 22; bx++) {
                for (int bz = z - 10; bz <= z + 2; bz++) {
                    if (level.getBlockState(new BlockPos(bx, y - 1, bz)).is(Blocks.SOUL_SAND) && random.nextDouble() < 0.35) {
                        set(level, bx, y, bz, wart);
                    }
                }
            }
            for (int[] p : new int[][] {{x + 11, z - 9}, {x + 21, z - 9}, {x + 11, z + 1}, {x + 21, z + 1}}) {
                for (int h = 0; h <= 6; h++) {
                    set(level, p[0], y + h, p[1], Blocks.NETHER_BRICK_FENCE.defaultBlockState());
                }
            }

            room(level, random, x - 22, y, z - 12, x - 12, y + 6, z - 2, MINE, POOL_FLOOR);
            for (int bx = x - 22; bx <= x - 12; bx++) {
                for (int bz = z - 12; bz <= z - 2; bz++) {
                    boolean ledge = bz >= z - 4;
                    for (int h = 0; h <= 3; h++) {
                        set(level, bx, y + h, bz, ledge ? MINE.pick(random) : Blocks.WATER.defaultBlockState());
                    }
                    if (!ledge && random.nextDouble() < 0.35) {
                        set(level, bx, y, bz, Blocks.SEAGRASS.defaultBlockState());
                    }
                }
            }

            room(level, random, x - 22, y, z - 34, x - 14, y + 5, z - 24, VAULT, VAULT_FLOOR);
            for (int bx = x - 22; bx <= x - 14; bx++) {
                if (bx == x - 18) {
                    continue;
                }
                BlockState shelf = (bx % 3 == 0 ? Blocks.BARREL : Blocks.BOOKSHELF).defaultBlockState();
                set(level, bx, y, z - 24, shelf);
                set(level, bx, y + 1, z - 24, Blocks.BOOKSHELF.defaultBlockState());
            }
            log("built the underground set");
            return null;
        }).join();
    }

    private void summon(Minecraft mc, String type, Vec3 at, double yaw, String extra) {
        cmd(mc, "summon minecraft:%s %.3f %.3f %.3f {Silent:1b,PersistenceRequired:1b,Tags:[\"scene\"],"
                + "Rotation:[%.1ff,0f]%s}", type, at.x, at.y, at.z, yaw, extra);
    }

    private static String holding(String item) {
        return ",equipment:{mainhand:{id:\"minecraft:" + item + "\",count:1}}";
    }

    private void walkers(Minecraft mc, Walker... walkers) {
        for (Walker w : walkers) {
            Vec3 p = w.at(0);
            String extra = ",NoAI:1b,Tags:[\"scene\",\"" + w.tag() + "\"]";
            String type = w.tag().startsWith("pig") ? "pig" : "zombie";
            String gear = type.equals("pig") ? ",Fire:30000s,Invulnerable:1b"
                    : holding(w.tag().endsWith("l") ? "lantern" : "torch");
            cmd(mc, "summon minecraft:%s %.3f %.3f %.3f {Silent:1b,PersistenceRequired:1b,Rotation:[%.1ff,0f]%s%s}",
                    type, p.x, p.y, p.z, w.yaw(), extra, gear);
        }
    }

    private void march(Minecraft mc, int f, Walker... walkers) {
        if (f % 3 != 0) {
            return;
        }
        for (Walker w : walkers) {
            Vec3 p = w.at(f / 3.0);
            cmd(mc, "tp @e[tag=%s,limit=1] %.3f %.3f %.3f %.1f 0", w.tag(), p.x, p.y, p.z, w.yaw());
        }
    }

    private static void kit(Minecraft mc, String... items) {
        run(mc, "clear @p");
        for (int i = 0; i < items.length; i++) {
            cmd(mc, "item replace entity @p hotbar.%d with %s", i, items[i].contains(":") ? items[i]
                    : "minecraft:" + items[i]);
        }
    }

    private static void throwItem(Minecraft mc, String item, double speed, double lift) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        Vec3 from = eye.add(look.scale(0.6)).add(0, -0.3, 0);
        cmd(mc, "summon minecraft:item %.3f %.3f %.3f {Item:{id:\"minecraft:%s\",count:1},PickupDelay:32767,"
                + "Tags:[\"scene\"],Motion:[%.3fd,%.3fd,%.3fd]}", from.x, from.y, from.z, item,
                look.x * speed, look.y * speed + lift, look.z * speed);
        swing(mc);
    }

    private static void shoot(Minecraft mc, Vec3 from, Vec3 velocity) {
        cmd(mc, "summon minecraft:arrow %.3f %.3f %.3f {Fire:2000s,NoGravity:1b,pickup:0b,crit:1b,Tags:[\"scene\"],"
                + "Motion:[%.3fd,%.3fd,%.3fd]}", from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
    }

    private void scene(Minecraft mc, WickConfig config, Vec3 feet, Runnable setup, Runnable hold, String... kit) {
        once(() -> {
            Screens.open(mc, null);
            config.resetToDefaults();
            cursorShown = false;
            showHand = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            run(mc, "gamemode creative @p");
            cmd(mc, "tp @p %.3f %.3f %.3f", feet.x, feet.y, feet.z);
            run(mc, "kill @e[type=!minecraft:player]");
            kit(mc, kit);
            select(mc, 0);
        });
        holdFor(80, () -> {
            place(mc, feet);
            hold.run();
        });
        once(() -> run(mc, "kill @e[type=minecraft:item]"));
        holdFor(20, () -> {
            place(mc, feet);
            hold.run();
        });
        once(setup);
        settle(mc, () -> {
            place(mc, feet);
            hold.run();
        });
    }

    private void plan(Minecraft mc) {
        WickConfig config = WickClient.config();

        until(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null);
        once(() -> {
            log("world loaded");
            Clock.fix();
            config.resetToDefaults();
            run(mc, "time set 2500");
            run(mc, "weather clear 1000000");
            run(mc, "gamerule advance_time false");
            run(mc, "gamerule spawn_mobs false");
            run(mc, "gamerule random_tick_speed 0");
            run(mc, "difficulty easy");
        });
        locate(mc);
        once(() -> {
            BlockPos c = site.get();
            cmd(mc, "forceload add %d %d %d %d", c.getX() - 40, c.getZ() - TUNNEL - 20, c.getX() + 40, c.getZ() + 40);
            cmd(mc, "tp @p %d %d %d", c.getX(), c.getY() + 45, c.getZ());
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        });
        settle(mc, () -> {
        });
        once(() -> level(mc));
        once(() -> buildMeadow(mc));
        once(() -> buildUnderground(mc));
        holdFor(60, () -> {
        });
        once(() -> run(mc, "time set 18000"));
        once(() -> film(mc, config));
    }

    private void film(Minecraft mc, WickConfig config) {
        if (wanted("intro")) {
            Vec3 start = u(0, 0, -20);
            Vec3 end = u(0, 0, -34);
            scene(mc, config, start, () -> showHand = true, () -> face(mc, 180, 6), "torch");
            record("01-intro", 330, f -> {
                place(mc, lerp(start, end, smooth(f / 330.0) * 0.85 + f / 330.0 * 0.15));
                face(mc, 180 - 3 * Math.sin(f / 60.0), 6 + 2 * Math.sin(f / 50.0));
                if (f == 240) {
                    shot("intro");
                }
            });
        }

        if (wanted("walk")) {
            Vec3 start = u(0, 0, -0.5);
            Vec3 end = u(0, 0, -15);
            scene(mc, config, start, () -> showHand = true, () -> face(mc, 180, 8), "torch");
            record("02-walk", 360, f -> {
                place(mc, lerp(start, end, smooth(f / 360.0) * 0.85 + f / 360.0 * 0.15));
                face(mc, 180 + 4 * Math.sin(f / 70.0), 8 + 2 * Math.sin(f / 45.0));
                if (f == 200) {
                    shot("walk");
                }
            });
        }

        if (wanted("throw")) {
            Vec3 feet = u(0, 0, -16);
            scene(mc, config, feet, () -> showHand = true, () -> face(mc, 180, -2), "torch 64");
            record("03-throw", 330, f -> {
                place(mc, feet);
                face(mc, 180, -2 + 3 * smooth(f / 330.0));
                if (f == 30) {
                    throwItem(mc, "torch", 0.55, 0.18);
                } else if (f == 110) {
                    throwItem(mc, "torch", 0.75, 0.2);
                } else if (f == 190) {
                    throwItem(mc, "torch", 0.95, 0.22);
                }
                if (f == 300) {
                    shot("throw");
                }
            });
        }

        if (wanted("mobs")) {
            Walker[] line = {
                    new Walker("m0", at(-6, 0, -4.5), -90, 0.07),
                    new Walker("pig", at(-8.6, 0, -3.4), -90, 0.07),
                    new Walker("m1l", at(-11, 0, -5.2), -90, 0.07),
                    new Walker("m2", at(-14.5, 0, -4.2), -90, 0.07)};
            Vec3 feet = at(0, 0, 2);
            scene(mc, config, feet, () -> walkers(mc, line),
                    () -> lookAt(mc, line[2].at(0).add(0, 1.0, 0)));
            record("04-mobs", 330, f -> {
                place(mc, feet);
                march(mc, f, line);
                lookAt(mc, line[2].at(f / 3.0).add(0, 1.0, 0));
                if (f == 220) {
                    shot("mobs");
                }
            });
        }

        if (wanted("glowing")) {
            Vec3 feet = u(16, 0, -0.5);
            Key left = new Key(0, 20, u(13, 1.2, -5));
            Key right = new Key(290, 0, u(19, 1.2, -5));
            scene(mc, config, feet, () -> {
                summon(mc, "blaze", u(16, 1.8, -5), 0, ",NoAI:1b,NoGravity:1b");
                summon(mc, "magma_cube", u(12.8, 0, -6.5), 35, ",NoAI:1b,Size:3");
                summon(mc, "magma_cube", u(19.2, 0, -5.5), -30, ",NoAI:1b,Size:2");
                summon(mc, "magma_cube", u(16.3, 0, -8.6), 5, ",NoAI:1b,Size:1");
            }, () -> follow(mc, 0, left));
            record("05-glowing", 330, f -> {
                place(mc, feet);
                follow(mc, f, left, right);
                if (f == 160) {
                    shot("glowing");
                }
            });
        }

        if (wanted("arrows")) {
            Vec3 feet = u(0, 0, -0.5);
            scene(mc, config, feet, () -> showHand = true, () -> face(mc, 180, 0), "bow");
            record("06-arrows", 330, f -> {
                place(mc, feet);
                face(mc, 180, 0);
                Vec3 from = mc.player.getEyePosition().add(0.25, -0.25, -0.8);
                if (f == 20) {
                    shoot(mc, from, new Vec3(-0.02, 0.0, -1.6));
                } else if (f == 105) {
                    shoot(mc, from, new Vec3(0.035, 0.012, -1.6));
                } else if (f == 190) {
                    shoot(mc, from, new Vec3(-0.045, -0.006, -1.6));
                }
                if (f == 60) {
                    shot("arrows");
                }
            });
        }

        if (wanted("water")) {
            Vec3 ledge = u(-17, 4, -3);
            Vec3 edge = u(-17, 4, -5.2);
            Vec3 deep = u(-17, 0.6, -8.2);
            scene(mc, config, ledge, () -> showHand = true, () -> face(mc, 180, 22), "torch", "lantern");
            record("07-water", 360, f -> {
                Vec3 p;
                if (f < 70) {
                    p = lerp(ledge, edge, smooth(f / 70.0));
                } else {
                    p = lerp(edge, deep, smooth((f - 70) / 90.0));
                }
                place(mc, p);
                face(mc, 180, lerp(22, 6, smooth((f - 60) / 100.0)));
                if (f == 215) {
                    select(mc, 1);
                }
                if (f == 190) {
                    shot("water-torch");
                } else if (f == 320) {
                    shot("water-lantern");
                }
            });
        }

        if (wanted("enchanted")) {
            Vec3 feet = u(-18, 0, -27.5);
            String sword = "minecraft:diamond_sword[minecraft:enchantments={\"minecraft:sharpness\":5}]";
            scene(mc, config, feet, () -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                run(mc, "item replace entity @p armor.chest with "
                        + "minecraft:diamond_chestplate[minecraft:enchantments={\"minecraft:protection\":4}]");
                for (int side : new int[] {-1, 1}) {
                    summon(mc, "armor_stand", u(-18 + side * 2.4, 0, -26.2), 180,
                            ",ShowArms:1b,equipment:{mainhand:{id:\"minecraft:netherite_sword\",count:1,"
                                    + "components:{\"minecraft:enchantments\":{\"minecraft:sharpness\":5}}},"
                                    + "chest:{id:\"minecraft:diamond_chestplate\",count:1,"
                                    + "components:{\"minecraft:enchantments\":{\"minecraft:protection\":4}}},"
                                    + "head:{id:\"minecraft:diamond_helmet\",count:1,"
                                    + "components:{\"minecraft:enchantments\":{\"minecraft:protection\":4}}}}");
                }
            }, () -> face(mc, 180, 10), sword);
            record("08-enchanted", 330, f -> {
                place(mc, feet);
                face(mc, 180, 10);
                if (f == 130) {
                    config.setEnchantedGlow(true);
                }
                if (f == 100) {
                    shot("enchanted-off");
                } else if (f == 280) {
                    shot("enchanted-on");
                }
            });
            once(() -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        }

        if (wanted("settings")) {
            Vec3 feet = u(0, 0, -6.5);
            scene(mc, config, feet, () -> {
            }, () -> face(mc, 180, 5), "torch");
            once(() -> Screens.open(mc, new WickSettingsScreen(null, mc.options)));
            holdFor(20, () -> {
                place(mc, feet);
                cursor(0, new Stop(0, () -> corner(mc)));
            });
            record("09-settings", 330, f -> {
                place(mc, feet);
                if (f < 235) {
                    cursor(f, new Stop(0, () -> corner(mc)),
                            new Stop(45, () -> labelAt(mc, "Enchanted Glow", 0.5)),
                            new Stop(110, () -> labelAt(mc, "Updates", 0.5)),
                            new Stop(165, () -> sliderAt(mc, "Range", 0.75)),
                            new Stop(215, () -> corner(mc)));
                }
                if (f == 55) {
                    clickLabel(mc, "Enchanted Glow", 0.5);
                } else if (f == 120) {
                    clickLabel(mc, "Updates", 0.5);
                } else if (f == 175) {
                    clickSlider(mc, "Range", 0.75);
                } else if (f == 210) {
                    shot("settings");
                } else if (f == 235) {
                    cursorShown = false;
                    Screens.open(mc, null);
                }
            });
            once(() -> Screens.open(mc, null));
        }

        if (wanted("outro")) {
            Vec3 start = u(0, 0, -0.5);
            Vec3 end = u(0, 0, -5);
            scene(mc, config, start, () -> {
                for (int i = 0; i < 4; i++) {
                    Vec3 p = u((i % 2 == 0 ? -1.2 : 1.2), 0.2, -9 - i * 12);
                    cmd(mc, "summon minecraft:item %.3f %.3f %.3f {Item:{id:\"minecraft:torch\",count:1},"
                            + "PickupDelay:32767,Age:-32768,Tags:[\"scene\"]}", p.x, p.y, p.z);
                }
            }, () -> face(mc, 180, 4));
            record("10-outro", 360, f -> {
                place(mc, lerp(start, end, smooth(f / 360.0)));
                face(mc, 180, 4);
                if (f == 120) {
                    shot("outro");
                }
            });
        }
    }

    private static double[] corner(Minecraft mc) {
        Screen screen = Screens.current(mc);
        return new double[] {screen.width - 36, screen.height - 30};
    }

    private static double[] labelAt(Minecraft mc, String label, double along) {
        AbstractWidget widget = find(Screens.current(mc), label);
        if (widget == null) {
            throw new IllegalStateException("no widget " + label);
        }
        return new double[] {widget.getX() + widget.getWidth() * along, widget.getY() + widget.getHeight() / 2.0};
    }

    private static void clickLabel(Minecraft mc, String label, double along) {
        double[] at = labelAt(mc, label, along);
        Ui.click(Screens.current(mc), at[0], at[1]);
    }

    private static double[] sliderAt(Minecraft mc, String label, double value) {
        AbstractWidget widget = find(Screens.current(mc), label);
        if (widget == null) {
            throw new IllegalStateException("no slider " + label);
        }
        return new double[] {widget.getX() + 4 + value * (widget.getWidth() - 8), widget.getY() + widget.getHeight() / 2.0};
    }

    private static void clickSlider(Minecraft mc, String label, double value) {
        double[] at = sliderAt(mc, label, value);
        Ui.click(Screens.current(mc), at[0], at[1]);
    }

    private static AbstractWidget find(GuiEventListener node, String label) {
        if (node instanceof AbstractWidget widget && widget.getMessage().getString().startsWith(label + ":")) {
            return widget;
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                AbstractWidget found = find(child, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void deleteWorld(Minecraft mc) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        if (Files.exists(dir)) {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    static void log(String message) {
        System.out.println("[wick-showcase] " + message);
    }
}
