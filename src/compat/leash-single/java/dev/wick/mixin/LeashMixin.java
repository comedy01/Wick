package dev.wick.mixin;

import dev.wick.core.Lights;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
abstract class LeashMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void wick$leashes(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        EntityRenderState.LeashState leash = state.leashState;
        if (leash == null || Lights.current().count() == 0) {
            return;
        }
        leash.startBlockLight = Math.max(leash.startBlockLight, blockLevel(leash.start));
        leash.endBlockLight = Math.max(leash.endBlockLight, blockLevel(leash.end));
    }

    private static int blockLevel(Vec3 point) {
        if (point == null) {
            return 0;
        }
        BlockPos pos = BlockPos.containing(point);
        return Lights.packedAt(pos.getX(), pos.getY(), pos.getZ()) / 16;
    }
}
