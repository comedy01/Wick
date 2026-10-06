package dev.wick.selftest;

import net.minecraft.client.Minecraft;

final class Frames {
    private Frames() {
    }

    static int fps(Minecraft mc) {
        return mc.getFps();
    }

    static void unlock(Minecraft mc) {
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
    }
}
