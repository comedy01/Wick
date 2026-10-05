package dev.wick.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WickConfigTest {
    @Test
    void itemIdsWithoutANamespaceMeanMinecraft() {
        assertEquals("minecraft:stick", WickConfig.normalizeId("stick"));
        assertEquals("minecraft:stick", WickConfig.normalizeId(" Minecraft:Stick "));
        assertEquals("othermod:glow_rod", WickConfig.normalizeId("othermod:glow_rod"));
    }

    @Test
    void customLevelsAreFoundByTheirFullId() {
        WickConfig config = new WickConfig();
        config.setItemLevel("stick", 12);
        assertEquals(12, config.itemLevel("minecraft:stick"));
        assertEquals(-1, config.itemLevel("minecraft:bone"));
    }
}
