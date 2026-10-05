package dev.wick.showcase.mixin;

import dev.wick.showcase.Showcase;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftFrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void wickShowcase$frame(boolean advanceGameTime, CallbackInfo ci) {
        Showcase.frame();
    }
}
