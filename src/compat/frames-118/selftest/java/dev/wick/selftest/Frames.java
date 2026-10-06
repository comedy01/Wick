package dev.wick.selftest;

import net.minecraft.client.Minecraft;

final class Frames {
    private Frames() {
    }

    static int fps(Minecraft mc) {
        String text = mc.fpsString;
        int space = text.indexOf(' ');
        try {
            return Integer.parseInt(space < 0 ? text : text.substring(0, space));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static void unlock(Minecraft mc) {
        mc.options.enableVsync = false;
        mc.getWindow().updateVsync(false);
        mc.options.framerateLimit = 260;
        mc.getWindow().setFramerateLimit(260);
    }
}
