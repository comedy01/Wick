package dev.wick.mixin;

import dev.wick.client.BlockCompat;
import dev.wick.core.Lights;
import dev.wick.core.Packed;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
abstract class LevelRendererLightMixin {
    @Inject(method = "getLightColor(Lnet/minecraft/client/renderer/LevelRenderer$BrightnessGetter;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void wick$dynamic(LevelRenderer.BrightnessGetter getter, BlockAndTintGetter level,
                                     BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int dynamic = Lights.packedAt(pos.getX(), pos.getY(), pos.getZ());
        if (dynamic > 0 && !(BlockCompat.solid(state) && BlockCompat.solid(level.getBlockState(pos)))) {
            cir.setReturnValue(Packed.withDynamic(cir.getReturnValueI(), dynamic));
        }
    }
}
