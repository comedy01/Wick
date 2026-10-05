package dev.wick.mixin;

import dev.wick.client.BlockCompat;
import dev.wick.core.Lights;
import dev.wick.core.Packed;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator", remap = false)
abstract class IndigoLightMixin {
    @Inject(method = "getLightmapCoordinates", at = @At("RETURN"), cancellable = true, require = 0)
    private static void wick$dynamic(BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int dynamic = Lights.packedAt(pos.getX(), pos.getY(), pos.getZ());
        if (dynamic > 0 && !(BlockCompat.solid(state) && BlockCompat.solid(level.getBlockState(pos)))) {
            cir.setReturnValue(Packed.withDynamic(cir.getReturnValueI(), dynamic));
        }
    }
}
