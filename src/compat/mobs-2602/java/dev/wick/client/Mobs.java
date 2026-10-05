package dev.wick.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.squid.GlowSquid;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

final class Mobs {
    private Mobs() {
    }

    static int glow(Entity entity) {
        if (entity instanceof MagmaCube cube) {
            return Math.min(10, 5 + cube.getSize());
        }
        if (entity instanceof GlowSquid squid) {
            return squid.getDarkTicksRemaining() > 0 ? 0 : 8;
        }
        if (entity instanceof DragonFireball) {
            return 12;
        }
        if (entity instanceof Fireball) {
            return 14;
        }
        if (entity instanceof WitherSkull) {
            return 6;
        }
        if (entity instanceof SpectralArrow) {
            return 8;
        }
        if (entity instanceof AbstractMinecart minecart) {
            return minecart.getDisplayBlockState().getLightEmission();
        }
        return 0;
    }

    static BlockState carried(Entity entity) {
        return entity instanceof EnderMan enderman ? enderman.getCarriedBlock() : null;
    }

    static ItemStack body(LivingEntity living) {
        return living.getItemBySlot(EquipmentSlot.BODY);
    }
}
