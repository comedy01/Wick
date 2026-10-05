package dev.wick.mixin.sodium;

import dev.wick.core.Lights;
import me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.model.light.smooth.AoFaceData", remap = false)
abstract class AoFaceDataMixin {
    @Unique
    private static final Direction[][] WICK_FACES = new Direction[6][];

    static {
        WICK_FACES[Direction.DOWN.ordinal()] = new Direction[] {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
        WICK_FACES[Direction.UP.ordinal()] = new Direction[] {Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH};
        WICK_FACES[Direction.NORTH.ordinal()] = new Direction[] {Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST};
        WICK_FACES[Direction.SOUTH.ordinal()] = new Direction[] {Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP};
        WICK_FACES[Direction.WEST.ordinal()] = new Direction[] {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH};
        WICK_FACES[Direction.EAST.ordinal()] = new Direction[] {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH};
    }

    @Unique
    private static final int[][] WICK_CORNERS = {{3, 0}, {2, 0}, {2, 1}, {3, 1}};

    @Shadow
    @Final
    public int[] lm;

    @Inject(method = "initLightData", at = @At("TAIL"), require = 0)
    private void wick$dynamic(LightDataAccess cache, BlockPos pos, Direction direction, boolean offset, CallbackInfo ci) {
        if (!Lights.vertexLight) {
            Lights.vertexLight = true;
            LogManager.getLogger("wick").info("Sodium found: smooth dynamic light per vertex");
        }
        Lights.Snapshot lights = Lights.current();
        if (lights.count() == 0) {
            return;
        }
        double half = offset ? 0.5 : 0.0;
        double cx = pos.getX() + 0.5 + direction.getStepX() * half;
        double cy = pos.getY() + 0.5 + direction.getStepY() * half;
        double cz = pos.getZ() + 0.5 + direction.getStepZ() * half;
        int step = offset ? 1 : 0;
        int bx = pos.getX() + direction.getStepX() * step;
        int by = pos.getY() + direction.getStepY() * step;
        int bz = pos.getZ() + direction.getStepZ() * step;
        Direction[] faces = WICK_FACES[direction.ordinal()];
        int[] lm = this.lm;
        for (int i = 0; i < 4; i++) {
            Direction a = faces[WICK_CORNERS[i][0]];
            Direction b = faces[WICK_CORNERS[i][1]];
            int dynamic = lights.packedAtPoint(
                    cx + 0.5 * (a.getStepX() + b.getStepX()),
                    cy + 0.5 * (a.getStepY() + b.getStepY()),
                    cz + 0.5 * (a.getStepZ() + b.getStepZ()),
                    bx, by, bz);
            if (dynamic > (lm[i] & 0xFF)) {
                lm[i] = (lm[i] & 0xFF0000) | dynamic;
            }
        }
    }
}
