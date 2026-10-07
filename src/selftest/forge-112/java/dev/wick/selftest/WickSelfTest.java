package dev.wick.selftest;

import dev.wick.client.LightHook;
import dev.wick.client.LightTracker;
import dev.wick.client.WickClient;
import dev.wick.client.gui.WickSettingsScreen;
import dev.wick.config.WickConfig;
import dev.wick.core.Lights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

@Mod(modid = "wick_selftest", name = "Wick self test", version = "1.0.0", clientSideOnly = true,
        dependencies = "required-after:wick")
public final class WickSelfTest {
    private static final Logger LOGGER = LogManager.getLogger("wick-selftest");
    private static final String WORLD = "wick-selftest";
    private static final int TIMEOUT = 2400;
    private static final int FLOOR = 4;

    private static final class Step {
        final int delay;
        final BooleanSupplier ready;
        final Runnable action;

        Step(int delay, BooleanSupplier ready, Runnable action) {
            this.delay = delay;
            this.ready = ready;
            this.action = action;
        }
    }

    private final List<Step> steps = new ArrayList<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int delay;
    private int waited;
    private long dirtiedBefore;
    private int worstMove;
    private double darkFloor = -1;
    private double torchFloor = -1;
    private double crowdFloor = -1;
    private long stressStart;
    private int stressFrames;
    private static int frames;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            frames++;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tick();
        }
    }

    private void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.currentScreen instanceof GuiMainMenu && ++idle > 40) {
                started = true;
                mc.gameSettings.pauseOnLostFocus = false;
                mc.gameSettings.chatVisibility = net.minecraft.entity.player.EntityPlayer.EnumChatVisibility.HIDDEN;
                plan(mc);
                delay = steps.get(0).delay;
                log("creating world");
                deleteWorld(new File(new File(mc.gameDir, "saves"), WORLD));
                WorldSettings settings = new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.FLAT);
                settings.enableCommands();
                mc.launchIntegratedServer(WORLD, WORLD, settings);
            }
            return;
        }
        if (delay > 0) {
            delay--;
            return;
        }
        Step step = steps.get(index);
        if (!step.ready.getAsBoolean()) {
            if (++waited > TIMEOUT) {
                finish(mc, new AssertionError("timed out at step " + index));
            }
            return;
        }
        waited = 0;
        index++;
        try {
            step.action.run();
        } catch (Throwable e) {
            finish(mc, e);
            return;
        }
        if (index >= steps.size()) {
            finish(mc, null);
        } else {
            delay = steps.get(index).delay;
        }
    }

    private void then(int ticks, Runnable action) {
        steps.add(new Step(ticks, () -> true, action));
    }

    private void when(BooleanSupplier ready, Runnable action) {
        steps.add(new Step(0, ready, action));
    }

    private void within(int ticks, BooleanSupplier ready, Runnable action) {
        int[] waited = {0};
        steps.add(new Step(0, () -> ready.getAsBoolean() || ++waited[0] > ticks, action));
    }

    private void plan(Minecraft mc) {
        WickConfig config = WickClient.config();
        int floor = FLOOR;
        BlockPos probe = new BlockPos(0, floor, -4);
        BlockPos farProbe = new BlockPos(8, floor, -4);
        BlockPos westProbe = new BlockPos(-6, floor, -4);

        when(() -> mc.world != null && mc.player != null && mc.currentScreen == null
                && mc.getIntegratedServer() != null, () -> {
            log("world loaded");
            run(mc, "tp @p 0.5 " + floor + " 0.5 180 25");
        });
        then(60, () -> {
            config.resetToDefaults();
            config.clearItemLevels();
            run(mc, "time set 18000");
            run(mc, "gamerule doDaylightCycle false");
            run(mc, "gamerule doMobSpawning false");
            run(mc, "gamerule doWeatherCycle false");
            run(mc, "gamerule doFireTick false");
            run(mc, "fill -12 " + (floor - 1) + " -12 12 " + (floor + 10) + " 12 minecraft:stone 0 hollow");
            run(mc, "tp @p 0.5 " + floor + " 0.5 180 25");
            run(mc, "replaceitem entity @p slot.weapon.mainhand minecraft:air");
            run(mc, "summon minecraft:armor_stand 0.5 " + floor + " -5.5 {NoGravity:1b,Rotation:[0f,0f],ShowArms:1b}");
        });
        within(200, () -> blockLight(mc, probe) == 0 && LightTracker.sources() == 0, () -> {
            check(blockLight(mc, probe) == 0, "the sealed room is not dark: " + blockLight(mc, probe));
            check(LightTracker.sources() == 0, "lights before anything glows: " + LightTracker.sources());
        });
        then(40, () -> darkFloor = screenshot(mc, "dark"));

        then(5, () -> {
            dirtiedBefore = LightTracker.totalDirtied();
            run(mc, "replaceitem entity @p slot.weapon.mainhand minecraft:torch");
        });
        within(40, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "holding a torch made " + LightTracker.sources() + " lights");
            int light = blockLight(mc, probe);
            check(light >= 9 && light <= 10, "4 blocks from a held torch the light is " + light);
            int meshed = block(new ChunkCache(mc.world, probe.add(-1, -1, -1), probe.add(1, 1, 1), 1).getCombinedLight(probe, 0));
            check(meshed == light, "the chunk mesh sees light " + meshed + ", not " + light);
            check(blockLight(mc, westProbe) >= 5, "7 blocks from a held torch the light is " + blockLight(mc, westProbe));
            long dirtied = LightTracker.totalDirtied() - dirtiedBefore;
            check(dirtied > 0 && dirtied <= 27, "a new torch rebuilt " + dirtied + " sections");
            log("torch rebuilt " + dirtied + " sections");
            Entity stand = find(mc, EntityArmorStand.class);
            check(stand != null, "the armor stand is missing on the client");
            check(block(stand.getBrightnessForRender()) >= 8, "the armor stand is not lit: " + block(stand.getBrightnessForRender()));
        });
        then(20, () -> torchFloor = screenshot(mc, "torch"));

        for (int i = 1; i <= 8; i++) {
            double x = i + 0.5;
            then(2, () -> {
                dirtiedBefore = LightTracker.totalDirtied();
                run(mc, String.format(Locale.ROOT, "tp @p %.1f %d 0.5 180 25", x, floor));
            });
            within(20, () -> mc.player.posX == x, () -> check(mc.player.posX == x, "the player did not move to " + x));
            then(2, () -> worstMove = Math.max(worstMove, (int) (LightTracker.totalDirtied() - dirtiedBefore)));
        }
        then(5, () -> {
            log("8 one-block moves, worst rebuild " + worstMove + " sections");
            check(worstMove <= 36, "a one-block move rebuilt " + worstMove + " sections");
            check(blockLight(mc, farProbe) >= 9, "the light did not follow the player: " + blockLight(mc, farProbe));
            check(blockLight(mc, westProbe) == 0, "the old spot is still lit: " + blockLight(mc, westProbe));
        });
        then(20, () -> screenshot(mc, "walked"));
        then(5, () -> run(mc, "tp @p 0.5 " + floor + " 0.5 180 25"));
        within(40, () -> blockLight(mc, probe) >= 9,
                () -> check(blockLight(mc, probe) >= 9, "back at the start the torch light is " + blockLight(mc, probe)));

        BlockPos nearSide = new BlockPos(0, floor, -1);
        String wall = "-11 " + floor + " -2 11 " + (floor + 9) + " -2";
        then(5, () -> run(mc, "fill " + wall + " minecraft:stone"));
        within(40, () -> blockLight(mc, probe) == 0, () -> {
            check(blockLight(mc, probe) == 0, "the torch shines through a wall: " + blockLight(mc, probe));
            check(blockLight(mc, nearSide) >= 12, "this side of the wall went dark: " + blockLight(mc, nearSide));
        });
        then(20, () -> {
            screenshot(mc, "wall");
            config.setWallsBlockLight(false);
        });
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
            run(mc, "fill " + wall + " minecraft:stone");
            run(mc, "fill 3 " + floor + " -2 4 " + (floor + 1) + " -2 minecraft:air");
        });
        then(20, () -> {
            int around = blockLight(mc, probe);
            check(around >= 1 && around <= 7, "light around the doorway should be dimmer but there: " + around);
            screenshot(mc, "doorway");
            run(mc, "fill " + wall + " minecraft:air");
        });

        then(10, () -> run(mc, "replaceitem entity @p slot.weapon.mainhand minecraft:air"));
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "the light stayed after the torch was put away");
            check(blockLight(mc, probe) == 0, "the room stayed lit: " + blockLight(mc, probe));
            run(mc, "replaceitem entity @p slot.weapon.offhand minecraft:glowstone");
        });
        within(20, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "glowstone in the off hand gives no light");
            check(blockLight(mc, probe) >= 10, "4 blocks from glowstone the light is " + blockLight(mc, probe));
            run(mc, "replaceitem entity @p slot.weapon.offhand minecraft:air");
        });
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "the glowstone's light stayed after it was put away");
            run(mc, "replaceitem entity @p slot.armor.head minecraft:lit_pumpkin");
        });
        within(20, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a jack o'lantern worn on the head gives no light");
            run(mc, "replaceitem entity @p slot.armor.head minecraft:air");
        });
        within(20, () -> LightTracker.sources() == 0,
                () -> check(LightTracker.sources() == 0, "the worn jack o'lantern kept glowing after it came off"));

        then(10, () -> run(mc, "summon minecraft:item -4.5 " + floor + " -6.5 {Item:{id:\"minecraft:torch\",Count:1b},PickupDelay:32767s,Age:-32768s}"));
        within(40, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a dropped torch gives no light");
            check(blockLight(mc, new BlockPos(-4, floor, -5)) >= 10, "next to a dropped torch it is dark");
        });
        then(20, () -> {
            screenshot(mc, "dropped");
            run(mc, "fill -6 " + floor + " -8 -3 " + floor + " -5 minecraft:water");
        });
        within(40, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "a dropped torch keeps burning underwater");
            run(mc, "fill -11 " + floor + " -11 11 " + (floor + 9) + " 11 minecraft:air 0 replace minecraft:water");
            run(mc, "fill -11 " + floor + " -11 11 " + (floor + 9) + " 11 minecraft:air 0 replace minecraft:flowing_water");
            run(mc, "kill @e[type=minecraft:item]");
        });

        then(10, () -> run(mc, "summon minecraft:blaze 5.5 " + floor + " -6.5 {NoAI:1b,Silent:1b}"));
        within(40, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a blaze gives no light");
            check(blockLight(mc, new BlockPos(5, floor, -4)) >= 6, "next to a blaze it is dark");
        });
        then(20, () -> {
            screenshot(mc, "blaze");
            run(mc, "kill @e[type=minecraft:blaze]");
        });
        glows(mc, "minecraft:magma_cube", "5.5 " + floor + " -6.5 {NoAI:1b,Size:1}", "a magma cube");
        glows(mc, "minecraft:enderman", "5.5 " + floor + " -6.5 {NoAI:1b,Silent:1b,carried:\"minecraft:glowstone\",carriedData:0s}", "an enderman carrying glowstone");
        glows(mc, "minecraft:minecart", "5.5 " + floor + " -6.5 {CustomDisplayTile:1b,DisplayTile:\"minecraft:glowstone\"}", "a minecart carrying glowstone");
        glows(mc, "minecraft:ender_crystal", "5.5 " + floor + " -6.5 {ShowBottom:0b}", "an end crystal");

        then(10, () -> run(mc, "summon minecraft:zombie -5.5 " + floor + " -2.5 "
                + "{NoAI:1b,Silent:1b,PersistenceRequired:1b,HandItems:[{id:\"minecraft:torch\",Count:1b},{}]}"));
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
            run(mc, "tp @e[type=minecraft:pig] 3.5 -100 3.5");
            run(mc, "kill @e[type=minecraft:pig]");
        });

        within(40, () -> LightTracker.sources() == 0, () -> {
            run(mc, "fill -2 " + floor + " -2 2 " + (floor + 2) + " 2 minecraft:water");
            run(mc, "tp @p 0.5 " + floor + " 0.5 180 25");
            run(mc, "replaceitem entity @p slot.weapon.mainhand minecraft:torch");
        });
        then(40, () -> {
            check(mc.player.isInsideOfMaterial(net.minecraft.block.material.Material.WATER), "the player is not underwater");
            check(LightTracker.sources() == 0, "a torch still burns underwater");
            run(mc, "replaceitem entity @p slot.weapon.mainhand minecraft:glowstone");
        });
        within(20, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "glowstone went out underwater");
            config.setWaterSensitive(false);
            run(mc, "replaceitem entity @p slot.weapon.mainhand minecraft:torch");
        });
        then(20, () -> {
            check(LightTracker.sources() == 1, "Water Puts Out Flames off still puts out the torch");
            config.setWaterSensitive(true);
            run(mc, "fill -2 " + floor + " -2 2 " + (floor + 2) + " 2 minecraft:air");
            run(mc, "replaceitem entity @p slot.weapon.mainhand minecraft:diamond_sword 1 0 {ench:[{id:16s,lvl:1s}]}");
        });
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "an enchanted sword glows with Enchanted Glow off");
            config.setEnchantedGlow(true);
        });
        within(20, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "an enchanted sword gives no light with Enchanted Glow on");
            check(eyeLight(mc) == 4 * 16, "an enchanted sword shines at " + eyeLight(mc) / 16.0);
            run(mc, "replaceitem entity @p slot.weapon.mainhand minecraft:enchanted_book 1 0 {StoredEnchantments:[{id:16s,lvl:1s}]}");
        });
        then(20, () -> {
            check(LightTracker.sources() == 1 && eyeLight(mc) == 4 * 16, "an enchanted book shines at " + eyeLight(mc) / 16.0);
            config.setEnchantedGlow(false);
            run(mc, "replaceitem entity @p slot.weapon.mainhand minecraft:stick");
        });
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "a plain stick gives light");
            config.setItemLevel("stick", 12);
        });
        within(20, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, "a stick set to 12 in wick.json (no namespace) gives no light");
            check(eyeLight(mc) == 12 * 16, "a level-12 stick shines at " + eyeLight(mc) / 16.0);
            config.clearItemLevels();
        });
        within(20, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "the stick kept glowing after its custom level was removed");
            run(mc, "replaceitem entity @p slot.weapon.mainhand minecraft:torch");
        });

        if (Loader.isModLoaded("baubles")) {
            then(5, () -> {
                config.setItemLevel("baubles:ring", 15);
                run(mc, "replaceitem entity @p slot.weapon.mainhand minecraft:air");
                setBauble(mc, "baubles:ring");
            });
            within(40, () -> LightTracker.sources() == 1, () -> {
                check(LightTracker.sources() == 1, "a ring in a Baubles slot gives no light");
                check(eyeLight(mc) == 15 * 16, "a ring in a Baubles slot shines at " + eyeLight(mc) / 16.0);
                setBauble(mc, null);
            });
            within(40, () -> LightTracker.sources() == 0, () -> {
                check(LightTracker.sources() == 0, "the Baubles ring kept glowing after it was taken off");
                log("Baubles slot lit and went dark");
                config.clearItemLevels();
                run(mc, "replaceitem entity @p slot.weapon.mainhand minecraft:torch");
            });
        }
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
            log("whole run: " + LightTracker.timings() + ", fps " + Minecraft.getDebugFPS());
            run(mc, "replaceitem entity @p slot.weapon.mainhand minecraft:air");
        });

        then(10, () -> {
            mc.gameSettings.limitFramerate = 260;
            mc.gameSettings.enableVsync = false;
            for (int i = 0; i < 40; i++) {
                run(mc, String.format(Locale.ROOT, "summon minecraft:zombie %d %d %d "
                                + "{Silent:1b,PersistenceRequired:1b,HandItems:[{id:\"minecraft:torch\",Count:1b},{}]}",
                        -10 + (i % 8) * 3, floor, -10 + (i / 8) * 4));
            }
        });
        within(60, () -> LightTracker.sources() == 40, () -> {
            check(LightTracker.sources() == 40, "40 torch zombies made " + LightTracker.sources() + " lights");
            restartStress();
        });
        for (int i = 0; i < 4; i++) {
            int shot = i;
            then(40, () -> {
                double floorLight = screenshot(mc, "stress-walls-" + shot);
                if (shot == 3) {
                    crowdFloor = floorLight;
                }
            });
        }
        then(40, () -> {
            log("stress, walls on: " + LightTracker.timings() + ", " + fps());
            config.setWallsBlockLight(false);
            restartStress();
        });
        then(160, () -> {
            log("stress, walls off: " + LightTracker.timings() + ", " + fps());
            config.setWallsBlockLight(true);
            config.setEnabled(false);
            restartStress();
        });
        then(160, () -> {
            log("stress, Wick off: " + fps());
            config.setEnabled(true);
            run(mc, "kill @e[type=minecraft:zombie]");
        });
        within(60, () -> LightTracker.sources() == 0, () -> {
            check(LightTracker.sources() == 0, "the zombies' lights outlived them");
            mc.displayGuiScreen(FMLClientHandler.instance()
                    .getGuiFactoryFor(Loader.instance().getIndexedModList().get(WickClient.MOD_ID))
                    .createConfigGui(null));
        });
        then(10, () -> {
            check(mc.currentScreen instanceof WickSettingsScreen, "settings screen not open: " + mc.currentScreen);
            screenshot(mc, "settings");
            config.setBurning(false);
            click(mc.currentScreen, "Reset to Defaults");
        });
        then(10, () -> {
            check(mc.currentScreen instanceof WickSettingsScreen, "settings screen gone after reset: " + mc.currentScreen);
            check(config.burning(), "Reset to Defaults did not turn Burning Entities back on");
            click(mc.currentScreen, "Burning Entities: ON");
            check(!config.burning(), "the Burning Entities button did nothing");
            click(mc.currentScreen, "Burning Entities: OFF");
            check(config.burning(), "the Burning Entities button did not turn back on");
            mc.displayGuiScreen(null);
            check(new File(Loader.instance().getConfigDir(), WickConfig.FILE_NAME).isFile(), "wick.json was not saved");
        });
        then(5, () -> {
            log(String.format(Locale.ROOT, "drawn floor brightness: dark %.1f, torch %.1f, 40 torch zombies %.1f",
                    darkFloor, torchFloor, crowdFloor));
            check(torchFloor >= darkFloor + 12.0, "the floor is not drawn lit by the held torch");
            check(crowdFloor >= darkFloor + 12.0, "the floor is not drawn lit among the torch zombies");
            check(LightHook.ran(), "the light hook never ran");
        });
    }

    private void glows(Minecraft mc, String type, String where, String what) {
        then(10, () -> run(mc, "summon " + type + " " + where));
        within(40, () -> LightTracker.sources() == 1, () -> {
            check(LightTracker.sources() == 1, what + " gives no light");
            run(mc, "tp @e[type=" + type + "] 5.5 -100 -6.5");
            run(mc, "kill @e[type=" + type + "]");
        });
        within(40, () -> LightTracker.sources() == 0, () -> check(LightTracker.sources() == 0, what + "'s light outlived it"));
    }

    private void restartStress() {
        LightTracker.resetTimings();
        stressFrames = frames;
        stressStart = System.nanoTime();
    }

    private String fps() {
        double seconds = (System.nanoTime() - stressStart) / 1e9;
        double fps = (frames - stressFrames) / seconds;
        return String.format(Locale.ROOT, "%.0f fps (%.2f ms/frame)", fps, 1000.0 / fps);
    }

    private static void setBauble(Minecraft mc, String id) {
        IntegratedServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> {
            try {
                net.minecraft.entity.player.EntityPlayerMP player = server.getPlayerList().getPlayers().get(0);
                Object handler = Class.forName("baubles.api.BaublesApi")
                        .getMethod("getBaublesHandler", net.minecraft.entity.player.EntityPlayer.class).invoke(null, player);
                net.minecraft.item.ItemStack stack = id == null ? net.minecraft.item.ItemStack.EMPTY
                        : new net.minecraft.item.ItemStack(net.minecraft.item.Item.getByNameOrId(id));
                ((net.minecraftforge.items.IItemHandlerModifiable) handler).setStackInSlot(1, stack);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static int blockLight(Minecraft mc, BlockPos pos) {
        return block(mc.world.getCombinedLight(pos, 0));
    }

    private static int block(int packed) {
        return (packed & 0xFFFF) >> 4;
    }

    private static int eyeLight(Minecraft mc) {
        BlockPos eye = new BlockPos(mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ);
        return Lights.packedAt(eye.getX(), eye.getY(), eye.getZ());
    }

    private static Entity find(Minecraft mc, Class<? extends Entity> type) {
        for (Entity entity : mc.world.loadedEntityList) {
            if (type.isInstance(entity)) {
                return entity;
            }
        }
        return null;
    }

    private static void click(GuiScreen screen, String label) {
        List<GuiButton> buttons = ReflectionHelper.getPrivateValue(GuiScreen.class, screen, "buttonList", "field_146292_n");
        for (GuiButton button : buttons) {
            if (button.displayString.equals(label)) {
                Method mouseClicked = ReflectionHelper.findMethod(
                        GuiScreen.class, "mouseClicked", "func_73864_a", int.class, int.class, int.class);
                try {
                    mouseClicked.invoke(screen, button.x + 1, button.y + 1, 0);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
                return;
            }
        }
        throw new AssertionError("no button \"" + label + "\"");
    }

    private static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> server.getCommandManager().executeCommand(server, command));
    }

    private static double screenshot(Minecraft mc, String name) {
        BufferedImage image = ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        File dir = new File(mc.gameDir, "screenshots");
        dir.mkdirs();
        try {
            ImageIO.write(image, "png", new File(dir, "wk-" + name + ".png"));
        } catch (IOException e) {
            log("could not save screenshot " + name + ": " + e);
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
        double brightness = count == 0 ? 0.0 : sum / count;
        log(String.format(Locale.ROOT, "screenshot %s, floor brightness %.1f", name, brightness));
        return brightness;
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    private void finish(Minecraft mc, Throwable failure) {
        finished = true;
        if (failure == null) {
            log("ALL CHECKS PASSED");
        } else {
            log("FAILED " + failure);
            LOGGER.error("self-test failure", failure);
        }
        mc.shutdown();
    }

    private static void deleteWorld(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteWorld(child);
            }
        }
        file.delete();
    }

    private static void log(String message) {
        LOGGER.info("[selftest] " + message);
    }
}
