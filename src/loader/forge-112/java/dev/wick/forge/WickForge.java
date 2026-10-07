package dev.wick.forge;

import dev.wick.client.LightTracker;
import dev.wick.client.WickClient;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;

@Mod(
        modid = WickClient.MOD_ID,
        useMetadata = true,
        clientSideOnly = true,
        acceptableRemoteVersions = "*",
        acceptedMinecraftVersions = "[1.12.2]",
        guiFactory = "dev.wick.forge.WickGuiFactory")
public final class WickForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        WickClient.init(event.getModConfigurationDirectory().toPath());
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientRegistry.registerKeyBinding(WickClient.createToggleKey());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            LightTracker.tick(Minecraft.getMinecraft());
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.getWorld().isRemote) {
            event.getWorld().addEventListener(new BlockListener());
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getWorld().isRemote) {
            LightTracker.chunkLoaded(event.getChunk().x, event.getChunk().z);
        }
    }

    private static final class BlockListener implements IWorldEventListener {
        @Override
        public void notifyBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
            LightTracker.blockChanged(pos, oldState, newState);
        }

        @Override
        public void notifyLightSet(BlockPos pos) {
        }

        @Override
        public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
            if ((x2 >> 4) - (x1 >> 4) > 4 || (z2 >> 4) - (z1 >> 4) > 4) {
                return;
            }
            for (int cx = x1 >> 4; cx <= x2 >> 4; cx++) {
                for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) {
                    LightTracker.chunkLoaded(cx, cz);
                }
            }
        }

        @Override
        public void playSoundToAllNearExcept(@Nullable EntityPlayer player, SoundEvent sound, SoundCategory category,
                                             double x, double y, double z, float volume, float pitch) {
        }

        @Override
        public void playRecord(SoundEvent sound, BlockPos pos) {
        }

        @Override
        public void spawnParticle(int id, boolean ignoreRange, double x, double y, double z,
                                  double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        }

        @Override
        public void spawnParticle(int id, boolean ignoreRange, boolean minimiseLevel, double x, double y, double z,
                                  double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        }

        @Override
        public void onEntityAdded(Entity entity) {
        }

        @Override
        public void onEntityRemoved(Entity entity) {
        }

        @Override
        public void broadcastSound(int soundId, BlockPos pos, int data) {
        }

        @Override
        public void playEvent(EntityPlayer player, int type, BlockPos pos, int data) {
        }

        @Override
        public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {
        }
    }
}
