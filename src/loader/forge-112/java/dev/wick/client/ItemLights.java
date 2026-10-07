package dev.wick.client;

import dev.wick.config.WickConfig;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class ItemLights {
    private static final Map<String, Integer> EXTRA = new HashMap<>();
    private static final Set<String> FLAMES = new HashSet<>(Arrays.asList(
            "minecraft:lava_bucket",
            "minecraft:blaze_rod",
            "minecraft:blaze_powder",
            "minecraft:fire_charge",
            "minecraft:magma_cream",
            "minecraft:lit_pumpkin"));

    static {
        EXTRA.put("minecraft:lava_bucket", 15);
        EXTRA.put("minecraft:nether_star", 12);
        EXTRA.put("minecraft:blaze_rod", 10);
        EXTRA.put("minecraft:fire_charge", 10);
        EXTRA.put("minecraft:blaze_powder", 8);
        EXTRA.put("minecraft:glowstone_dust", 8);
        EXTRA.put("minecraft:spectral_arrow", 8);
        EXTRA.put("minecraft:magma_cream", 6);
        EXTRA.put("minecraft:experience_bottle", 6);
        EXTRA.put("minecraft:prismarine_crystals", 6);
    }

    public static final int ENCHANTED = 4;

    private static final Map<Item, String> IDS = new IdentityHashMap<>();

    private ItemLights() {
    }

    public static int level(ItemStack stack, boolean underwater, WickConfig config) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Item item = stack.getItem();
        String id = IDS.computeIfAbsent(item, ItemLights::id);
        if (underwater && config.waterSensitive() && isFlame(id)) {
            return 0;
        }
        int level = config.itemLevel(id);
        if (level < 0) {
            level = builtIn(stack, item, id);
        }
        return Math.max(level, enchanted(stack, config));
    }

    public static int enchanted(ItemStack stack, WickConfig config) {
        if (!config.enchantedGlow() || stack == null || stack.isEmpty()) {
            return 0;
        }
        return stack.hasEffect() ? ENCHANTED : 0;
    }

    private static String id(Item item) {
        ResourceLocation name = item.getRegistryName();
        return name == null ? "" : name.toString();
    }

    @SuppressWarnings("deprecation")
    static int builtIn(ItemStack stack, Item item, String id) {
        Integer extra = EXTRA.get(id);
        if (extra != null) {
            return extra;
        }
        if (item instanceof ItemBlock) {
            try {
                IBlockState state = ((ItemBlock) item).getBlock().getStateFromMeta(item.getMetadata(stack.getMetadata()));
                return state.getLightValue();
            } catch (RuntimeException e) {
                return ((ItemBlock) item).getBlock().getDefaultState().getLightValue();
            }
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
