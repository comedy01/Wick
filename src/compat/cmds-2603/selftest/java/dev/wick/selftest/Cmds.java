package dev.wick.selftest;

final class Cmds {
    static final String[] FREEZE = {"advance_time", "spawn_mobs"};
    static final String GLINT_STICK = "minecraft:stick[minecraft:enchantment_glint_override=true]";

    private Cmds() {
    }

    static String stack(String id) {
        return "{id:\"" + id + "\",count:1}";
    }

    static String holding(String id) {
        return "equipment:{mainhand:" + stack(id) + "}";
    }

    static String leash(int x, int y, int z) {
        return "leash:[I;" + x + "," + y + "," + z + "]";
    }

    static String lightBlock(int level) {
        return "minecraft:light[minecraft:block_state={level:\"" + level + "\"}]";
    }

    static String enchanted(String item, String enchantment, int level) {
        return item + "[minecraft:enchantments={\"" + enchantment + "\":" + level + "}]";
    }

    static String storedEnchantment(String enchantment, int level) {
        return "minecraft:enchanted_book[minecraft:stored_enchantments={\"" + enchantment + "\":" + level + "}]";
    }

    static String blockState(String id) {
        return "\"" + id + "\"";
    }
}
