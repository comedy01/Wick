package dev.wick.client;

import dev.wick.config.WickConfig;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

public final class EntityLights {
    private EntityLights() {
    }

    public static int level(Entity entity, Entity self, WickConfig config) {
        if (entity.isSpectator()) {
            return 0;
        }
        int level = 0;
        if (config.burning() && entity.isOnFire()) {
            level = 15;
        }
        if (config.heldItems() && entity instanceof LivingEntity living && (config.others() || entity == self)) {
            boolean underwater = living.isEyeInFluid(FluidTags.WATER);
            level = Math.max(level, ItemLights.level(living.getMainHandItem(), underwater, config));
            level = Math.max(level, ItemLights.level(living.getOffhandItem(), underwater, config));
            level = Math.max(level, ItemLights.level(living.getItemBySlot(EquipmentSlot.HEAD), underwater, config));
            level = Math.max(level, ItemLights.enchanted(living.getItemBySlot(EquipmentSlot.CHEST), config));
            level = Math.max(level, ItemLights.enchanted(living.getItemBySlot(EquipmentSlot.LEGS), config));
            level = Math.max(level, ItemLights.enchanted(living.getItemBySlot(EquipmentSlot.FEET), config));
            level = Math.max(level, ItemLights.enchanted(Mobs.body(living), config));
            if (Accessories.present()) {
                int[] worn = {level};
                Accessories.forEach(living, stack -> worn[0] = Math.max(worn[0], ItemLights.level(stack, underwater, config)));
                level = worn[0];
            }
            level = Math.max(level, emission(Mobs.carried(entity)));
        }
        if (config.droppedItems()) {
            if (entity instanceof ItemEntity item) {
                level = Math.max(level, ItemLights.level(item.getItem(), item.isInWater(), config));
            } else if (entity instanceof ItemFrame frame) {
                level = Math.max(level, ItemLights.level(frame.getItem(), inWater(frame), config));
            } else {
                level = Math.max(level, Displays.level(entity, config));
            }
        }
        if (config.glowingMobs()) {
            level = Math.max(level, glow(entity));
        }
        return level;
    }

    static boolean inWater(Entity entity) {
        return Vanilla.level(entity).getFluidState(entity.blockPosition()).is(FluidTags.WATER);
    }

    static int emission(BlockState state) {
        return state == null ? 0 : state.getLightEmission();
    }

    private static int glow(Entity entity) {
        int known = builtInGlow(entity);
        int defined = DataLights.entityLevel(entity);
        if (defined >= 0) {
            return Math.max(known, defined);
        }
        return known > 0 ? known : selfLit(entity);
    }

    public static final int SELF_LIT_MAX = 10;

    private static final Reference2BooleanOpenHashMap<EntityType<?>> MODDED = new Reference2BooleanOpenHashMap<>();

    private static int selfLit(Entity entity) {
        if (entity.isOnFire() || Displays.is(entity) || entity instanceof ItemFrame || !modded(entity.getType())) {
            return 0;
        }
        BlockPos pos = Vanilla.blockPos(entity.getLightProbePosition(1.0F));
        int drawn;
        try {
            drawn = RendererCompat.blockLight(entity, pos);
        } catch (RuntimeException e) {
            MODDED.put(entity.getType(), false);
            return 0;
        }
        int around = Vanilla.level(entity).getBrightness(LightLayer.BLOCK, pos);
        return drawn > around ? Math.min(drawn, SELF_LIT_MAX) : 0;
    }

    private static boolean modded(EntityType<?> type) {
        if (!MODDED.containsKey(type)) {
            MODDED.put(type, !Vanilla.entityNamespace(type).equals("minecraft"));
        }
        return MODDED.getBoolean(type);
    }

    private static int builtInGlow(Entity entity) {
        if (entity instanceof Blaze) {
            return 10;
        }
        if (entity instanceof Creeper creeper) {
            float swelling = creeper.getSwelling(1.0F);
            return swelling <= 0.0F ? 0 : swelling < 0.5F ? 5 : 10;
        }
        if (entity instanceof PrimedTnt) {
            return 10;
        }
        if (entity instanceof EndCrystal) {
            return 12;
        }
        if (entity instanceof LightningBolt) {
            return 15;
        }
        if (entity instanceof FallingBlockEntity falling) {
            return falling.getBlockState().getLightEmission();
        }
        return Mobs.glow(entity);
    }
}
