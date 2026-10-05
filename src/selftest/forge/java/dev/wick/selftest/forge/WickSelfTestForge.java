package dev.wick.selftest.forge;

import dev.wick.selftest.WickSelfTest;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("wick_selftest")
public final class WickSelfTestForge {
    public WickSelfTestForge() {
        WickSelfTest test = new WickSelfTest();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                test.tick();
            }
        });
    }
}
