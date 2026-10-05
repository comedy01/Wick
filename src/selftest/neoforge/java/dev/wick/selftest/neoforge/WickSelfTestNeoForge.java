package dev.wick.selftest.neoforge;

import dev.wick.selftest.WickSelfTest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "wick_selftest", dist = Dist.CLIENT)
public final class WickSelfTestNeoForge {
    public WickSelfTestNeoForge() {
        WickSelfTest test = new WickSelfTest();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> test.tick());
    }
}
