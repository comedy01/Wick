package dev.wick.mixin;

import dev.wick.client.LightTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void wick$blockChanged(BlockPos pos, BlockState before, BlockState after, int flags, CallbackInfo ci) {
        LightTracker.blockChanged(pos, before, after);
    }

    @Inject(method = "onChunkLoaded", at = @At("HEAD"))
    private void wick$chunkLoaded(int x, int z, CallbackInfo ci) {
        LightTracker.chunkLoaded(x, z);
    }
}
