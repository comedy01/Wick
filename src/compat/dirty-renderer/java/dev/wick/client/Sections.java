package dev.wick.client;

import net.minecraft.client.Minecraft;

final class Sections {
    private Sections() {
    }

    static void dirty(Minecraft mc, int x, int y, int z) {
        mc.levelRenderer.setSectionDirty(x, y, z);
    }
}
