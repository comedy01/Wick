package dev.wick.mixin.sodium;

import dev.wick.client.Vanilla;
import dev.wick.core.Lights;
import dev.wick.core.Packed;
import me.jellysquid.mods.sodium.client.render.entity.EntityLightSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.model.light.EntityLighter", remap = false)
abstract class EntityLighterMixin {
    @Inject(method = "getBlendedLight", at = @At("RETURN"), cancellable = true, require = 0)
    private static <T extends Entity> void wick$dynamic(EntityLightSampler<T> lighter, T entity, float partialTicks,
                                                        CallbackInfoReturnable<Integer> cir) {
        BlockPos pos = Vanilla.blockPos(entity.getLightProbePosition(partialTicks));
        int dynamic = Lights.packedAt(pos.getX(), pos.getY(), pos.getZ());
        if (dynamic > 0) {
            cir.setReturnValue(Packed.withDynamic(cir.getReturnValueI(), dynamic));
        }
    }
}
