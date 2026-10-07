package dev.wick.client;

import dev.wick.core.Lights;
import dev.wick.core.Packed;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.function.IntSupplier;

public final class LightHook {
    private static final ThreadLocal<Boolean> RAW = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static volatile boolean ran;

    private LightHook() {
    }

    public static int combined(int packed, IBlockAccess access, BlockPos pos) {
        ran = true;
        Lights.Snapshot lights = Lights.current();
        if (lights.count() == 0 || pos == null || (access instanceof World && !((World) access).isRemote)) {
            return packed;
        }
        if (RAW.get() || access.getBlockState(pos).isOpaqueCube()) {
            return packed;
        }
        int dynamic = lights.packedAt(pos.getX(), pos.getY(), pos.getZ());
        return dynamic > 0 ? Packed.withDynamic(packed, dynamic) : packed;
    }

    public static int raw(IntSupplier lookup) {
        boolean before = RAW.get();
        RAW.set(Boolean.TRUE);
        try {
            return lookup.getAsInt();
        } finally {
            RAW.set(before);
        }
    }

    public static boolean ran() {
        return ran;
    }
}
