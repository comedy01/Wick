package dev.wick.selftest;

final class Cmds {
    static final String[] FREEZE = {"doDaylightCycle", "doMobSpawning"};
    static final String GLINT_STICK = null;

    private Cmds() {
    }

    static String stack(String id) {
        return "{id:\"" + id + "\",Count:1b}";
    }

    static String holding(String id) {
        return "HandItems:[" + stack(id) + ",{}]";
    }

    static String leash(int x, int y, int z) {
        return "Leash:{X:" + x + ",Y:" + y + ",Z:" + z + "}";
    }

    static String lightBlock(int level) {
        return null;
    }

    static String enchanted(String item, String enchantment, int level) {
        return item + "{Enchantments:[{id:\"" + enchantment + "\",lvl:" + level + "s}]}";
    }

    static String storedEnchantment(String enchantment, int level) {
        return "minecraft:enchanted_book{StoredEnchantments:[{id:\"" + enchantment + "\",lvl:" + level + "s}]}";
    }

    static String blockState(String id) {
        return "{Name:\"" + id + "\"}";
    }
}
