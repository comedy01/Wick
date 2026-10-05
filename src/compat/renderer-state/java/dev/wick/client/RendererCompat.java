package dev.wick.client;

import dev.wick.mixin.EntityRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

final class RendererCompat {
    private RendererCompat() {
    }

    static int blockLight(Entity entity, BlockPos pos) {
        EntityRenderer<?, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        return renderer == null ? -1 : ((EntityRendererAccessor) renderer).wick$blockLight(entity, pos);
    }
}
