package dev.wick.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class Vanilla {
    private Vanilla() {
    }

    public static BlockPos blockPos(Vec3 pos) {
        return new BlockPos(pos);
    }

    static boolean removed(Entity entity) {
        return entity.removed;
    }

    public static Level level(Entity entity) {
        return entity.level;
    }

    static String itemId(Item item) {
        return Registry.ITEM.getKey(item).toString();
    }

    public static String entityId(EntityType<?> type) {
        return Registry.ENTITY_TYPE.getKey(type).toString();
    }

    static String entityNamespace(EntityType<?> type) {
        return Registry.ENTITY_TYPE.getKey(type).getNamespace();
    }
}
