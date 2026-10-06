package dev.wick.client;

import dev.wick.config.WickConfig;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;

public final class ItemLights {
    private static final Map<String, Integer> EXTRA = Map.ofEntries(
            Map.entry("minecraft:lava_bucket", 15),
            Map.entry("minecraft:nether_star", 12),
            Map.entry("minecraft:blaze_rod", 10),
            Map.entry("minecraft:fire_charge", 10),
            Map.entry("minecraft:glow_berries", 10),
            Map.entry("minecraft:glow_ink_sac", 10),
            Map.entry("minecraft:blaze_powder", 8),
            Map.entry("minecraft:glowstone_dust", 8),
            Map.entry("minecraft:spectral_arrow", 8),
            Map.entry("minecraft:magma_cream", 6),
            Map.entry("minecraft:experience_bottle", 6),
            Map.entry("minecraft:amethyst_shard", 4),
            Map.entry("minecraft:prismarine_crystals", 6));

    private static final Set<String> FLAMES = Set.of(
            "minecraft:lava_bucket",
            "minecraft:campfire",
            "minecraft:soul_campfire",
            "minecraft:blaze_rod",
            "minecraft:blaze_powder",
            "minecraft:fire_charge",
            "minecraft:magma_cream",
            "minecraft:jack_o_lantern");

    public static final int ENCHANTED = 4;

    private ItemLights() {
    }

    private static final Map<Item, String> IDS = new Reference2ObjectOpenHashMap<>();

    public static int level(ItemStack stack, boolean underwater, WickConfig config) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Item item = stack.getItem();
        String id = IDS.computeIfAbsent(item, Vanilla::itemId);
        boolean wet = underwater && config.waterSensitive();
        if (wet && isFlame(id)) {
            return 0;
        }
        int level = config.itemLevel(id);
        if (level < 0) {
            level = DataLights.itemLevel(stack, wet);
        }
        if (level < 0) {
            level = builtIn(stack, item, id);
        }
        return Math.max(level, enchanted(stack, config));
    }

    public static int enchanted(ItemStack stack, WickConfig config) {
        if (!config.enchantedGlow() || stack == null || stack.isEmpty()) {
            return 0;
        }
        boolean shimmers = stack.hasFoil() || ItemData.storedEnchantments(stack);
        return shimmers ? ENCHANTED : 0;
    }

    static int builtIn(ItemStack stack, Item item, String id) {
        Integer extra = EXTRA.get(id);
        if (extra != null) {
            return extra;
        }
        if (item instanceof BlockItem block) {
            BlockState state = ItemData.withStackState(stack, block.getBlock().defaultBlockState());
            return state.getLightEmission();
        }
        return 0;
    }

    static boolean isFlame(String id) {
        if (FLAMES.contains(id)) {
            return true;
        }
        return id.endsWith("torch") && !id.contains("redstone");
    }
}
