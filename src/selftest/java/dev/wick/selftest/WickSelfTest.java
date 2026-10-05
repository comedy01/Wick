package dev.wick.selftest;

import dev.wick.client.DataLights;
import dev.wick.client.LightTracker;
import dev.wick.client.WickClient;
import dev.wick.client.gui.WickSettingsScreen;
import dev.wick.config.WickConfig;
import dev.wick.core.Lights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class WickSelfTest {
    private static final String WORLD = "wick-selftest";
    private static final int TIMEOUT = 2400;

    private static volatile int frames;

    private final List<Step> steps = new ArrayList<>();
    private final List<String> shots = new ArrayList<>();
    private final Set<String> existingShots = new HashSet<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int delay;
    private int waited;
    private int seenFrames;
    private int patience;

    private long dirtiedBefore;
    private int stressFrames;
    private long stressStart;
    private int moves;
    private int worstMove;

    private record Step(int delay, BooleanSupplier ready, Runnable action) {
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 40) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.tutorialStep = TutorialSteps.NONE;
                ServerCompat.hideChat(mc);
                rememberShots(mc);
                plan(mc);
                delay = steps.get(0).delay();
                log("creating world");
                deleteWorld(mc, WORLD);
                Worlds.create(mc, WORLD, true, 0L);
            }
            return;
        }
        int drawn = frames;
        if (drawn == seenFrames) {
            return;
        }
        seenFrames = drawn;
        if (delay > 0) {
            delay--;
            return;
        }
        Step step = steps.get(index);
        if (!step.ready().getAsBoolean()) {
            if (++waited > TIMEOUT) {
                finish(mc, new AssertionError("timed out at step " + index));
            }
            return;
        }
        waited = 0;
        index++;
        try {
            step.action().run();
        } catch (Throwable e) {
            finish(mc, e);
            return;
        }
        if (index >= steps.size()) {
            finish(mc, null);
        } else {
            delay = steps.get(index).delay();
        }
    }

    public static void frame() {
        frames++;
    }

    private void then(int ticks, Runnable action) {
        steps.add(new Step(ticks, () -> true, action));
    }

    private void when(BooleanSupplier ready, Runnable action) {
        steps.add(new Step(0, ready, action));
    }

    private void within(int ticks, BooleanSupplier ready, Runnable action) {
        then(1, () -> patience = ticks);
        when(() -> ready.getAsBoolean() || --patience < 0, action);
    }

    private void finish(Minecraft mc, Throwable failure) {
        finished = true;
        nameShots(mc);
        if (failure == null) {
            failure = checkDrawnLight(mc);
        }
        if (failure == null) {
            log("ALL CHECKS PASSED");
        } else {
            log("FAILED: " + failure);
            failure.printStackTrace();
        }
        WickConfig config = WickClient.config();
        config.resetToDefaults();
        config.clearItemLevels();
        WickClient.saveConfig();
        mc.stop();
    }

    private void plan(Minecraft mc) {
        WickConfig config = WickClient.config();
        int floor = ServerCompat.FLOOR;
        BlockPos probe = new BlockPos(0, floor, -4);
        BlockPos farProbe = new BlockPos(8, floor, -4);
        BlockPos westProbe = new BlockPos(-6, floor, -4);

        when(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null, () -> log("world loaded, renderer: "
                + (sodium() ? "sodium" : "vanilla")));
        then(40, () -> {
            config.resetToDefaults();
            config.clearItemLevels();
            run(mc, "time set 18000");
            for (String rule : Cmds.FREEZE) {
                run(mc, "gamerule " + rule + " false");
            }
            run(mc, "fill -12 " + (floor - 1) + " -12 12 " + (floor + 10) + " 12 minecraft:stone hollow");
            run(mc, "tp @p 0.5 " + floor + " 0.5 180 25");
            run(mc, "item replace entity @p weapon.mainhand with minecraft:air");
            run(mc, "summon minecraft:armor_stand 0.5 " + floor + " -5.5 {NoGravity:1b,Rotation:[0f,0f],ShowArms:1b}");
        });
        within(200, () -> blockLight(mc, probe) == 0 && LightTracker.sources() == 0, () -> {
            check(blockLight(mc, probe) == 0, "the sealed room is not dark: " + blockLight(mc, probe));
            check(LightTracker.sources() == 0, "lights before anything glows: " + LightTracker.sources());
        });
        then(40, () -> screenshot(mc, "dark"));

        then(5, () -> {
            dirtiedBefore = LightTracker.totalDirtied();
            run(mc, "item replace entity @p weapon.mainhand with minecraft:torch");
        });
        within(40, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "holding a torch made " + LightTracker.sources() + " lights");
            int light = blockLight(mc, probe);
            check(light >= 9 && light <= 10, "4 blocks from a held torch the light is " + light);
            int asked = block(Probe.packedAs(mc, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), probe));
            check(asked == light, "asked with a solid block's state the light is " + asked + ", not " + light);
            check(blockLight(mc, westProbe) >= 5, "7 blocks from a held torch the light is " + blockLight(mc, westProbe));
            long dirtied = LightTracker.totalDirtied() - dirtiedBefore;
            check(dirtied > 0 && dirtied <= 27, "a new torch rebuilt " + dirtied + " sections");
            log("torch rebuilt " + dirtied + " sections");
            Entity stand = find(mc, "minecraft:armor_stand");
            check(stand != null, "the armor stand is missing on the client");
            int packed = mc.getEntityRenderDispatcher().getRenderer(stand).getPackedLightCoords(stand, 1.0F);
            check(block(packed) >= 8, "the armor stand is not lit: " + block(packed));
            check(Lights.vertexLight == sodium(), sodium()
                    ? "Sodium's smooth lighting never ran Wick's vertex hook"
                    : "the Sodium vertex hook ran without Sodium");
        });
        then(20, () -> screenshot(mc, "torch"));

        for (int i = 1; i <= 8; i++) {
            double x = i + 0.5;
            then(2, () -> {
                dirtiedBefore = LightTracker.totalDirtied();
                run(mc, String.format(Locale.ROOT, "tp @p %.1f %d 0.5 180 25", x, floor));
            });
            within(20, () -> mc.player.getX() == x, () -> {
                check(mc.player.getX() == x, "the player did not move to " + x);
            });
            then(2, () -> {
                int cost = (int) (LightTracker.totalDirtied() - dirtiedBefore);
                moves++;
                worstMove = Math.max(worstMove, cost);
            });
        }
        then(5, () -> {
            log("8 one-block moves, worst rebuild " + worstMove + " sections");
            check(worstMove <= 36, "a one-block move rebuilt " + worstMove + " sections");
            check(blockLight(mc, farProbe) >= 9, "the light did not follow the player: " + blockLight(mc, farProbe));
            check(blockLight(mc, westProbe) == 0, "the old spot is still lit: " + blockLight(mc, westProbe));
        });
        then(20, () -> screenshot(mc, "walked"));
        then(5, () -> run(mc, "tp @p 0.5 " + floor + " 0.5 180 25"));
        within(40, () -> blockLight(mc, probe) >= 9, () -> {
            check(blockLight(mc, probe) >= 9, "back at the start the torch light is " + blockLight(mc, probe));
        });

        BlockPos nearSide = new BlockPos(0, floor, -1);
        String wall = "-11 " + floor + " -2 11 " + (floor + 9) + " -2";
        then(5, () -> run(mc, "fill " + wall + " minecraft:stone"));
        within(40, () -> blockLight(mc, probe) == 0, () -> {
            check(blockLight(mc, probe) == 0, "the torch shines through a wall: " + blockLight(mc, probe));
            check(blockLight(mc, nearSide) >= 12, "this side of the wall went dark: " + blockLight(mc, nearSide));
        });
        then(20, () -> screenshot(mc, "wall"));
        then(5, () -> config.setWallsBlockLight(false));
        within(40, () -> blockLight(mc, probe) >= 9, () -> {
            check(blockLight(mc, probe) >= 9, "Walls Block Light off still stops the light: " + blockLight(mc, probe));
            config.setWallsBlockLight(true);
        });
        within(40, () -> blockLight(mc, probe) == 0, () -> {
            check(blockLight(mc, probe) == 0, "Walls Block Light back on lets light through the wall");
            run(mc, "fill " + wall + " minecraft:air");
        });
        within(40, () -> blockLight(mc, probe) >= 9, () -> {
            check(blockLight(mc, probe) >= 9, "taking the wall down did not let the light back: " + blockLight(mc, probe));
        });

        String doorway = "3 " + floor + " -2 4 " + (floor + 9) + " -2";
        then(5, () -> {
            run(mc, "fill " + wall + " minecraft:stone");
            run(mc, "fill " + doorway + " minecraft:air");
        });
        within(40, () -> blockLight(mc, probe) < 9, () -> {
            int around = blockLight(mc, probe);
            log("light around the wall: " + around);
            check(around >= 3 && around <= 7, "light around the doorway should be dimmer but there: " + around);
        });
        then(20, () -> screenshot(mc, "doorway"));
        then(5, () -> run(mc, "fill " + wall + " minecraft:air"));
        within(40, () -> blockLight(mc, probe) >= 9, () -> {
            check(blockLight(mc, probe) >= 9, "taking the doorway wall down did not let the light back: " + blockLight(mc, probe));
        });

        if (Leashes.SUPPORTED) {
            then(5, () -> {
                run(mc, "setblock 2 " + floor + " -2 minecraft:oak_fence");
                run(mc, "summon minecraft:pig 1.5 " + floor + " -0.5 {NoAI:1b," + Cmds.leash(2, floor, -2) + "}");
            });
            within(40, () -> leash(mc) != null, () -> {
                int[] leash = leash(mc);
                check(leash != null, "the pig never showed a leash");
                log("leash light: start " + leash[0] + ", end " + leash[1]);
                check(leash[0] >= 10 && leash[1] >= 8, "the leash stayed dark beside the torch: " + leash[0] + " to " + leash[1]);
                run(mc, "kill @e[type=minecraft:pig]");
                run(mc, "kill @e[type=minecraft:leash_knot]");
                run(mc, "setblock 2 " + floor + " -2 minecraft:air");
            });
            then(10, () -> run(mc, "kill @e[type=minecraft:item]"));
        }

        then(10, () -> run(mc, "item replace entity @p weapon.mainhand with minecraft:air"));
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "the light stayed after the torch was put away");
            check(blockLight(mc, probe) == 0, "the room stayed lit: " + blockLight(mc, probe));
        });

        then(5, () -> run(mc, "item replace entity @p weapon.offhand with minecraft:lantern"));
        within(20, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a lantern in the off hand gives no light");
            check(blockLight(mc, probe) >= 10, "4 blocks from a lantern the light is " + blockLight(mc, probe));
        });
        then(5, () -> run(mc, "item replace entity @p weapon.offhand with minecraft:air"));
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "the lantern's light stayed after it was put away");
            run(mc, "item replace entity @p armor.head with minecraft:jack_o_lantern");
        });
        within(20, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a jack o'lantern worn on the head gives no light");
            run(mc, "item replace entity @p armor.head with minecraft:air");
        });
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "the worn jack o'lantern kept glowing after it came off");
            run(mc, "item replace entity @p weapon.mainhand with " + Cmds.lightBlock(5));
        });
        within(20, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a level-5 light block gives no light");
            BlockPos eye = BlockPos.containing(mc.player.getEyePosition());
            int packed = Lights.packedAt(eye.getX(), eye.getY(), eye.getZ());
            check(packed == 5 * 16, "a level-5 light block shines at " + packed / 16.0);
            run(mc, "item replace entity @p weapon.mainhand with minecraft:air");
        });

        then(10, () -> run(mc, "summon minecraft:item -4.5 " + floor + " -6.5 "
                + "{Item:" + Cmds.stack("minecraft:torch") + ",PickupDelay:32767,Age:-32768}"));
        within(40, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a dropped torch gives no light");
            check(blockLight(mc, new BlockPos(-4, floor, -5)) >= 10, "next to a dropped torch it is dark");
        });
        then(20, () -> screenshot(mc, "dropped"));
        then(5, () -> run(mc, "kill @e[type=minecraft:item]"));
        within(40, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "the dropped torch's light outlived it");
            run(mc, "summon minecraft:item_frame -4.5 " + floor + " -6.5 "
                    + "{Fixed:1b,Facing:1b,Item:" + Cmds.stack("minecraft:torch") + "}");
        });
        within(40, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a torch in an item frame gives no light");
            run(mc, "fill -6 " + floor + " -8 -4 " + floor + " -6 minecraft:water");
        });
        within(40, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "a torch in an item frame keeps burning underwater");
            run(mc, "kill @e[type=minecraft:item_frame]");
            run(mc, "fill -11 " + floor + " -11 11 " + (floor + 9) + " 11 minecraft:air replace minecraft:water");
        });

        then(10, () -> run(mc, "summon minecraft:blaze 5.5 " + floor + " -6.5 {NoAI:1b,Silent:1b}"));
        within(40, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a blaze gives no light");
            check(blockLight(mc, new BlockPos(5, floor, -4)) >= 6, "next to a blaze it is dark");
        });
        then(20, () -> screenshot(mc, "blaze"));
        then(5, () -> run(mc, "kill @e[type=minecraft:blaze]"));
        glows(mc, "minecraft:enderman", "5.5 " + floor + " -6.5 "
                + "{NoAI:1b,Silent:1b,carriedBlockState:" + Cmds.blockState("minecraft:glowstone") + "}", "an enderman carrying glowstone");
        glows(mc, "minecraft:item_display", "5.5 " + (floor + 1) + " -6.5 "
                + "{item:" + Cmds.stack("minecraft:glowstone") + "}", "an item display showing glowstone");
        glows(mc, "minecraft:block_display", "5 " + floor + " -7 "
                + "{block_state:" + Cmds.blockState("minecraft:sea_lantern") + "}", "a block display showing a sea lantern");
        glows(mc, "minecraft:furnace_minecart", "5.5 " + floor + " -6.5 {Fuel:30000s}", "a fuelled furnace minecart");
        glows(mc, "minecraft:end_crystal", "5.5 " + floor + " -6.5 {ShowBottom:0b}", "an end crystal");

        then(10, () -> run(mc, "summon minecraft:zombie -5.5 " + floor + " -2.5 "
                + "{NoAI:1b,Silent:1b,PersistenceRequired:1b," + Cmds.holding("minecraft:torch") + "}"));
        within(40, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a zombie holding a torch gives no light");
            config.setOthers(false);
        });
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "Other Players & Mobs off still lights the zombie's torch");
            config.setOthers(true);
            run(mc, "kill @e[type=minecraft:zombie]");
        });

        within(40, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "the zombie's light outlived it");
            run(mc, "summon minecraft:pig 3.5 " + floor + " 3.5 {NoAI:1b,Silent:1b,Invulnerable:1b,Fire:30000s}");
        });
        within(40, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a burning pig gives no light");
            config.setBurning(false);
        });
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "Burning Entities off still lights the pig");
            config.setBurning(true);
            run(mc, "kill @e[type=minecraft:pig]");
        });

        then(10, () -> {
            run(mc, "fill -2 " + floor + " -2 2 " + (floor + 2) + " 2 minecraft:water");
            run(mc, "item replace entity @p weapon.mainhand with minecraft:torch");
        });
        within(40, () -> mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), () -> {
            check(mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER), "the player is not underwater");
        });
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "a torch still burns underwater");
            run(mc, "item replace entity @p weapon.mainhand with minecraft:lantern");
        });
        within(20, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a lantern went out underwater");
            config.setWaterSensitive(false);
            run(mc, "item replace entity @p weapon.mainhand with minecraft:torch");
        });
        within(20, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "Water Puts Out Flames off still puts out the torch");
            config.setWaterSensitive(true);
            run(mc, "fill -2 " + floor + " -2 2 " + (floor + 2) + " 2 minecraft:air");
        });

        then(10, () -> run(mc, "item replace entity @p weapon.mainhand with "
                + Cmds.enchanted("minecraft:diamond_sword", "minecraft:sharpness", 5)));
        then(20, () -> {
            check(LightTracker.sources() == 0, "an enchanted sword glows with Enchanted Glow off");
            config.setEnchantedGlow(true);
        });
        within(20, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "an enchanted sword gives no light with Enchanted Glow on");
            BlockPos eye = BlockPos.containing(mc.player.getEyePosition());
            int packed = Lights.packedAt(eye.getX(), eye.getY(), eye.getZ());
            check(packed == 4 * 16, "an enchanted sword shines at " + packed / 16.0);
            run(mc, "item replace entity @p weapon.mainhand with " + Cmds.storedEnchantment("minecraft:mending", 1));
        });
        then(20, () -> {
            BlockPos eye = BlockPos.containing(mc.player.getEyePosition());
            int packed = Lights.packedAt(eye.getX(), eye.getY(), eye.getZ());
            check(LightTracker.sources() == 1 && packed == 4 * 16, "an enchanted book shines at " + packed / 16.0);
            if (Cmds.GLINT_STICK != null) {
                run(mc, "item replace entity @p weapon.mainhand with " + Cmds.GLINT_STICK);
            }
        });
        then(20, () -> {
            if (Cmds.GLINT_STICK != null) {
                BlockPos eye = BlockPos.containing(mc.player.getEyePosition());
                int packed = Lights.packedAt(eye.getX(), eye.getY(), eye.getZ());
                check(LightTracker.sources() == 1 && packed == 4 * 16, "a shimmering stick shines at " + packed / 16.0);
            }
            run(mc, "item replace entity @p weapon.mainhand with minecraft:air");
            run(mc, "item replace entity @p armor.chest with "
                    + Cmds.enchanted("minecraft:iron_chestplate", "minecraft:protection", 4));
        });
        then(20, () -> {
            check(LightTracker.sources() == 1, "worn enchanted armor gives no light with Enchanted Glow on");
            screenshot(mc, "enchanted");
            run(mc, "item replace entity @p weapon.mainhand with "
                    + Cmds.enchanted("minecraft:diamond_sword", "minecraft:sharpness", 5));
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        });
        then(30, () -> {
            screenshot(mc, "enchanted-front-on");
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        });
        then(30, () -> {
            screenshot(mc, "enchanted-back-on");
            config.setEnchantedGlow(false);
        });
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "Enchanted Glow off left the gear glowing");
        });
        then(30, () -> {
            screenshot(mc, "enchanted-back-off");
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        });
        then(30, () -> {
            screenshot(mc, "enchanted-front-off");
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            run(mc, "item replace entity @p armor.chest with minecraft:air");
            run(mc, "item replace entity @p weapon.mainhand with minecraft:air");
        });

        if (accessoryMod()) {
            then(10, () -> wear(mc, new ItemStack(Items.LANTERN)));
            within(40, () -> LightTracker.sources() == 1, () -> {
                BlockPos eye = BlockPos.containing(mc.player.getEyePosition());
                int packed = Lights.packedAt(eye.getX(), eye.getY(), eye.getZ());
                log("lantern on an accessory belt shines at " + packed / 16.0);
                check(LightTracker.sources() == 1 && packed == 15 * 16, "a lantern on an accessory belt shines at " + packed / 16.0);
                wear(mc, ItemStack.EMPTY);
            });
            within(40, () -> LightTracker.sources() == 0, () -> {
                check(LightTracker.sources() == 0, "the belt lantern kept glowing after it was taken off");
            });
        }

        then(10, () -> {
            config.setItemLevel("stick", 12);
            run(mc, "item replace entity @p weapon.mainhand with minecraft:stick");
        });
        within(20, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a stick set to 12 in wick.json (no namespace) gives no light");
            check(blockLight(mc, probe) >= 7, "a level-12 stick lights 4 blocks away to " + blockLight(mc, probe));
            config.clearItemLevels();
        });
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "the stick kept glowing after its custom level was removed");
            log("light files: " + DataLights.summary());
            run(mc, "item replace entity @p weapon.mainhand with minecraft:feather");
        });

        within(20, () -> LightTracker.sources() == 1, () -> {
            BlockPos eye = BlockPos.containing(mc.player.getEyePosition());
            int packed = Lights.packedAt(eye.getX(), eye.getY(), eye.getZ());
            check(packed == 9 * 16, "a feather set to 9 by a light file shines at " + packed / 16.0);
            run(mc, "item replace entity @p weapon.mainhand with minecraft:white_wool");
        });
        then(20, () -> {
            BlockPos eye = BlockPos.containing(mc.player.getEyePosition());
            int packed = Lights.packedAt(eye.getX(), eye.getY(), eye.getZ());
            check(packed == 15 * 16, "wool matched by tag and lit like glowstone shines at " + packed / 16.0);
            run(mc, "item replace entity @p weapon.mainhand with minecraft:air");
        });
        glows(mc, "minecraft:cow", "5.5 " + floor + " -6.5 {NoAI:1b}", "a cow lit by a light file");
        within(60, () -> LightTracker.sources() == 0, () -> run(mc, "summon minecraft:pig 5.5 " + floor + " -6.5 {NoAI:1b,Age:-24000}"));
        then(20, () -> {
            check(LightTracker.sources() == 0, "a light file with conditions Wick cannot check was used anyway");
            run(mc, "kill @e[type=minecraft:pig]");
            run(mc, "item replace entity @p weapon.mainhand with minecraft:torch");
        });

        within(20, () -> LightTracker.sources() == 1, () -> config.setEnabled(false));
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "turning Wick off left a light on");
            check(blockLight(mc, probe) == 0, "turning Wick off left the room lit");
            config.setEnabled(true);
        });
        within(20, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "turning Wick back on did not relight the torch");
            run(mc, "gamemode spectator @p");
        });
        within(40, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "a torch selected in spectator mode still lights the world");
            run(mc, "gamemode creative @p");
        });
        within(40, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "back in creative the torch gives no light");
            log("whole run: " + LightTracker.timings() + ", fps " + mc.getFps());
            run(mc, "item replace entity @p weapon.mainhand with minecraft:air");
        });

        then(10, () -> {
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(260);
            limitOnlyWhenMinimized(mc);
            for (int i = 0; i < 40; i++) {
                run(mc, String.format(Locale.ROOT, "summon minecraft:zombie %d %d %d "
                        + "{Silent:1b,PersistenceRequired:1b," + Cmds.holding("minecraft:torch") + "}",
                        -10 + (i % 8) * 3, floor, -10 + (i / 8) * 4));
            }
        });
        within(60, () -> LightTracker.sources() == 40, () -> {
            check(LightTracker.sources() == 40, "40 torch zombies made " + LightTracker.sources() + " lights");
            LightTracker.resetTimings();
            stressFrames = frames;
            stressStart = System.nanoTime();
        });
        for (int i = 0; i < 4; i++) {
            int shot = i;
            then(40, () -> screenshot(mc, "stress-walls-" + shot));
        }
        then(40, () -> {
            log("stress, walls on: " + LightTracker.timings() + ", " + frameTime());
            config.setWallsBlockLight(false);
            LightTracker.resetTimings();
            stressFrames = frames;
            stressStart = System.nanoTime();
        });
        for (int i = 0; i < 4; i++) {
            int shot = i;
            then(40, () -> screenshot(mc, "stress-open-" + shot));
        }
        then(40, () -> {
            log("stress, walls off: " + LightTracker.timings() + ", " + frameTime());
            config.setWallsBlockLight(true);
            config.setEnabled(false);
            stressFrames = frames;
            stressStart = System.nanoTime();
        });
        then(200, () -> {
            log("stress, Wick off: " + frameTime());
            config.setEnabled(true);
            run(mc, "kill @e[type=minecraft:zombie]");
        });
        within(80, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "the zombies' lights outlived them");
            Screens.open(mc, new WickSettingsScreen(null, mc.options));
        });
        then(20, () -> {
            check(Screens.current(mc) instanceof WickSettingsScreen, "the settings screen did not open");
            screenshot(mc, "settings");
        });
        then(5, () -> Screens.open(mc, null));
    }

    private void glows(Minecraft mc, String type, String where, String what) {
        within(60, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "something else still glows before " + what);
            run(mc, "summon " + type + " " + where);
        });
        within(40, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, what + " gives no light");
            run(mc, "kill @e[type=" + type + "]");
        });
        then(25, () -> run(mc, "kill @e[type=minecraft:item]"));
        within(60, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "the light of " + what + " outlived it");
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void limitOnlyWhenMinimized(Minecraft mc) {
        try {
            Object option = mc.options.getClass().getMethod("inactivityFpsLimit").invoke(mc.options);
            Class<? extends Enum> limits = (Class<? extends Enum>) Class.forName("net.minecraft.client.InactivityFpsLimit");
            option.getClass().getMethod("set", Object.class).invoke(option, Enum.valueOf(limits, "MINIMIZED"));
        } catch (ReflectiveOperationException e) {
        }
    }

    private String frameTime() {
        int drawn = frames - stressFrames;
        double seconds = (System.nanoTime() - stressStart) / 1e9;
        return String.format(Locale.ROOT, "%.0f fps (%.2f ms/frame)", drawn / seconds, seconds * 1000.0 / Math.max(1, drawn));
    }

    private static boolean sodium() {
        return WickSelfTest.class.getClassLoader()
                .getResource("net/caffeinemc/mods/sodium/client/model/light/smooth/AoFaceData.class") != null
                || WickSelfTest.class.getClassLoader()
                .getResource("me/jellysquid/mods/sodium/client/model/light/smooth/AoFaceData.class") != null;
    }

    private static int blockLight(Minecraft mc, BlockPos pos) {
        return block(Probe.packed(mc, pos));
    }

    private static int block(int packed) {
        return (packed & 0xFFFF) >> 4;
    }

    private static boolean accessoryMod() {
        return type("eu.pb4.trinkets.api.TrinketsApi") != null || type("top.theillusivec4.curios.api.CuriosApi") != null;
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name, false, WickSelfTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static void wear(Minecraft mc, ItemStack stack) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            try {
                Class<?> trinkets = type("eu.pb4.trinkets.api.TrinketsApi");
                if (trinkets != null) {
                    Object attachment = trinkets.getMethod("getAttachment", LivingEntity.class).invoke(null, player);
                    Map<?, ?> groups = (Map<?, ?>) type("eu.pb4.trinkets.api.TrinketAttachment").getMethod("getInventory").invoke(attachment);
                    Container belt = (Container) ((Map<?, ?>) groups.get("legs")).get("belt");
                    belt.setItem(0, stack.copy());
                }
                Class<?> curios = type("top.theillusivec4.curios.api.CuriosApi");
                if (curios != null) {
                    Object handler = ((Optional<?>) curios.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, player)).orElseThrow();
                    type("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler")
                            .getMethod("setEquippedCurio", String.class, int.class, ItemStack.class)
                            .invoke(handler, "belt", 0, stack.copy());
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                log("could not put " + stack + " on the belt: " + e);
                e.printStackTrace();
            }
        });
    }

    private static int[] leash(Minecraft mc) {
        Entity pig = find(mc, "minecraft:pig");
        return pig == null ? null : Leashes.light(mc, pig);
    }

    private static Entity find(Minecraft mc, String type) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(type)) {
                return entity;
            }
        }
        return null;
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> ServerCompat.runCommand(server, command));
    }

    private void screenshot(Minecraft mc, String name) {
        log("screenshot: " + name);
        shots.add(name);
        ServerCompat.screenshot(mc, Screens.renderTarget(mc));
    }

    private void rememberShots(Minecraft mc) {
        File[] files = new File(mc.gameDirectory, "screenshots").listFiles();
        if (files != null) {
            for (File file : files) {
                existingShots.add(file.getName());
            }
        }
    }

    private void nameShots(Minecraft mc) {
        File dir = new File(mc.gameDirectory, "screenshots");
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".png") && !existingShots.contains(name));
        if (files == null) {
            return;
        }
        List<File> fresh = new ArrayList<>(List.of(files));
        fresh.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        for (int i = 0; i < fresh.size() && i < shots.size(); i++) {
            File target = new File(dir, String.format(Locale.ROOT, "wk-%02d-%s.png", i, shots.get(i)));
            if ((!target.exists() || target.delete()) && fresh.get(i).renameTo(target)) {
                log("saved " + target.getName());
            }
        }
    }

    private Throwable checkDrawnLight(Minecraft mc) {
        File dir = new File(mc.gameDirectory, "screenshots");
        try {
            double dark = floorBrightness(new File(dir, String.format(Locale.ROOT, "wk-%02d-dark.png", shots.indexOf("dark"))));
            double torch = floorBrightness(new File(dir, String.format(Locale.ROOT, "wk-%02d-torch.png", shots.indexOf("torch"))));
            log(String.format(Locale.ROOT, "drawn floor brightness: dark %.1f, torch %.1f", dark, torch));
            if (torch < dark + 12.0) {
                return new AssertionError(String.format(Locale.ROOT,
                        "the floor is not drawn lit by the held torch (brightness %.1f, dark %.1f)", torch, dark));
            }
            int stress = shots.indexOf("stress-walls-3");
            if (stress >= 0) {
                double crowd = floorBrightness(new File(dir, String.format(Locale.ROOT, "wk-%02d-stress-walls-3.png", stress)));
                log(String.format(Locale.ROOT, "drawn floor brightness among 40 torch zombies: %.1f", crowd));
                if (crowd < dark + 12.0) {
                    return new AssertionError(String.format(Locale.ROOT,
                            "the floor is not drawn lit among the torch zombies (brightness %.1f, dark %.1f)", crowd, dark));
                }
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return new AssertionError("could not read the screenshots: " + e, e);
        }
    }

    private static double floorBrightness(File file) throws IOException {
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(file);
        if (image == null) {
            throw new IOException("not an image: " + file);
        }
        int x0 = image.getWidth() * 30 / 100;
        int x1 = image.getWidth() * 60 / 100;
        int y0 = image.getHeight() * 55 / 100;
        int y1 = image.getHeight() * 85 / 100;
        double sum = 0;
        int count = 0;
        for (int y = y0; y < y1; y += 2) {
            for (int x = x0; x < x1; x += 2) {
                int rgb = image.getRGB(x, y);
                sum += 0.299 * (rgb >> 16 & 0xFF) + 0.587 * (rgb >> 8 & 0xFF) + 0.114 * (rgb & 0xFF);
                count++;
            }
        }
        return sum / count;
    }

    private static void deleteWorld(Minecraft mc, String name) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(name);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void log(String message) {
        System.out.println("[wick-selftest] " + message);
    }
}
