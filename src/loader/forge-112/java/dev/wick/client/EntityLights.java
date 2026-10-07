package dev.wick.client;

import dev.wick.config.WickConfig;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityDragonFireball;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntitySpectralArrow;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;

import java.util.IdentityHashMap;
import java.util.Map;

public final class EntityLights {
    public static final int SELF_LIT_MAX = 10;

    private static final Map<Class<?>, Boolean> MODDED = new IdentityHashMap<>();

    private EntityLights() {
    }

    public static int level(Entity entity, Entity self, WickConfig config) {
        if (entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator()) {
            return 0;
        }
        int level = 0;
        if (config.burning() && entity.isBurning()) {
            level = 15;
        }
        if (config.heldItems() && entity instanceof EntityLivingBase && (config.others() || entity == self)) {
            EntityLivingBase living = (EntityLivingBase) entity;
            boolean underwater = living.isInsideOfMaterial(Material.WATER);
            level = Math.max(level, ItemLights.level(living.getHeldItemMainhand(), underwater, config));
            level = Math.max(level, ItemLights.level(living.getHeldItemOffhand(), underwater, config));
            level = Math.max(level, ItemLights.level(living.getItemStackFromSlot(EntityEquipmentSlot.HEAD), underwater, config));
            level = Math.max(level, ItemLights.enchanted(living.getItemStackFromSlot(EntityEquipmentSlot.CHEST), config));
            level = Math.max(level, ItemLights.enchanted(living.getItemStackFromSlot(EntityEquipmentSlot.LEGS), config));
            level = Math.max(level, ItemLights.enchanted(living.getItemStackFromSlot(EntityEquipmentSlot.FEET), config));
            if (entity instanceof EntityPlayer && Baubles.present()) {
                for (ItemStack stack : Baubles.worn((EntityPlayer) entity)) {
                    level = Math.max(level, ItemLights.level(stack, underwater, config));
                }
            }
            if (entity instanceof EntityEnderman) {
                level = Math.max(level, emission(((EntityEnderman) entity).getHeldBlockState()));
            }
        }
        if (config.droppedItems()) {
            if (entity instanceof EntityItem) {
                EntityItem item = (EntityItem) entity;
                level = Math.max(level, ItemLights.level(item.getItem(), item.isInWater(), config));
            } else if (entity instanceof EntityItemFrame) {
                EntityItemFrame frame = (EntityItemFrame) entity;
                boolean wet = frame.world.getBlockState(new BlockPos(frame)).getMaterial() == Material.WATER;
                level = Math.max(level, ItemLights.level(frame.getDisplayedItem(), wet, config));
            }
        }
        if (config.glowingMobs()) {
            level = Math.max(level, glow(entity));
        }
        return level;
    }

    @SuppressWarnings("deprecation")
    static int emission(IBlockState state) {
        return state == null ? 0 : state.getLightValue();
    }

    private static int glow(Entity entity) {
        int known = builtInGlow(entity);
        return known > 0 ? known : selfLit(entity);
    }

    private static int selfLit(Entity entity) {
        if (entity.isBurning() || entity instanceof EntityItemFrame || !modded(entity)) {
            return 0;
        }
        BlockPos pos = new BlockPos(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
        int drawn;
        try {
            drawn = (LightHook.raw(entity::getBrightnessForRender) & 0xFFFF) >> 4;
        } catch (RuntimeException e) {
            MODDED.put(entity.getClass(), false);
            return 0;
        }
        int around = entity.world.getLightFor(EnumSkyBlock.BLOCK, pos);
        return drawn > around ? Math.min(drawn, SELF_LIT_MAX) : 0;
    }

    private static boolean modded(Entity entity) {
        Boolean known = MODDED.get(entity.getClass());
        if (known == null) {
            ResourceLocation id = EntityList.getKey(entity);
            known = id != null && !"minecraft".equals(id.getNamespace());
            MODDED.put(entity.getClass(), known);
        }
        return known;
    }

    private static int builtInGlow(Entity entity) {
        if (entity instanceof EntityBlaze) {
            return 10;
        }
        if (entity instanceof EntityCreeper) {
            float swelling = ((EntityCreeper) entity).getCreeperFlashIntensity(1.0F);
            return swelling <= 0.0F ? 0 : swelling < 0.5F ? 5 : 10;
        }
        if (entity instanceof EntityTNTPrimed) {
            return 10;
        }
        if (entity instanceof EntityEnderCrystal) {
            return 12;
        }
        if (entity instanceof EntityLightningBolt) {
            return 15;
        }
        if (entity instanceof EntityFallingBlock) {
            return emission(((EntityFallingBlock) entity).getBlock());
        }
        if (entity instanceof EntityMagmaCube) {
            return Math.min(10, 5 + ((EntityMagmaCube) entity).getSlimeSize());
        }
        if (entity instanceof EntityDragonFireball) {
            return 12;
        }
        if (entity instanceof EntityWitherSkull) {
            return 6;
        }
        if (entity instanceof EntityFireball) {
            return 14;
        }
        if (entity instanceof EntitySpectralArrow) {
            return 8;
        }
        if (entity instanceof EntityMinecart) {
            return emission(((EntityMinecart) entity).getDisplayTile());
        }
        return 0;
    }
}
