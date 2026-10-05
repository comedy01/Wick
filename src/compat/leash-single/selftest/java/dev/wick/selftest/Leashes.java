package dev.wick.selftest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

final class Leashes {
    static final boolean SUPPORTED = true;

    private Leashes() {
    }

    static int[] light(Minecraft mc, Entity mob) {
        EntityRenderState state = mc.getEntityRenderDispatcher().getRenderer(mob).createRenderState(mob, 1.0F);
        EntityRenderState.LeashState leash = state.leashState;
        if (leash == null) {
            return null;
        }
        return new int[] {leash.startBlockLight, leash.endBlockLight};
    }
}
