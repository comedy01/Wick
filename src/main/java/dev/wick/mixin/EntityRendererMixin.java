package dev.wick.mixin;

import dev.wick.core.Lights;
import dev.wick.core.Packed;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
    private void wick$dynamic(Entity entity, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        BlockPos pos = BlockPos.containing(entity.getLightProbePosition(partialTicks));
        int dynamic = Lights.packedAt(pos.getX(), pos.getY(), pos.getZ());
        if (dynamic > 0) {
            cir.setReturnValue(Packed.withDynamic(cir.getReturnValueI(), dynamic));
        }
    }
}
