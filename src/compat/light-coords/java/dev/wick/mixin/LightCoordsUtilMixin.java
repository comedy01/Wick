package dev.wick.mixin;

import dev.wick.client.BlockCompat;
import dev.wick.core.Lights;
import dev.wick.core.Packed;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightCoordsUtil.class)
abstract class LightCoordsUtilMixin {
    @Inject(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void wick$dynamic(LightCoordsUtil.BrightnessGetter getter, BlockAndLightGetter level,
                                     BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int dynamic = Lights.packedAt(pos.getX(), pos.getY(), pos.getZ());
        if (dynamic > 0 && !(BlockCompat.solid(state) && BlockCompat.solid(level.getBlockState(pos)))) {
            cir.setReturnValue(Packed.withDynamic(cir.getReturnValueI(), dynamic));
        }
    }
}
