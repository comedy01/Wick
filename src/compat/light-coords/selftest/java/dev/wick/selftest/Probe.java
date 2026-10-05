package dev.wick.selftest;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.LightCoordsUtil;

final class Probe {
    private Probe() {
    }

    static int packed(Minecraft mc, BlockPos pos) {
        return LightCoordsUtil.getLightCoords(mc.level, pos);
    }

    static int packedAs(Minecraft mc, BlockState state, BlockPos pos) {
        return LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, mc.level, state, pos);
    }
}
