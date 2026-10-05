package dev.wick.client;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class DataCodecs {
    record ItemMatch(ItemPredicate predicate, List<Item> items) {
        boolean test(ItemStack stack) {
            return predicate.test(stack);
        }
    }

    private DataCodecs() {
    }

    static DynamicOps<JsonElement> ops(ClientLevel level) {
        return level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    static ItemMatch itemMatch(JsonElement json, DynamicOps<JsonElement> ops) {
        ItemPredicate predicate = ItemPredicate.CODEC.parse(ops, json).getOrThrow(IllegalArgumentException::new);
        List<Item> items = predicate.items().map(set -> {
            List<Item> list = new ArrayList<>();
            for (Holder<Item> holder : set) {
                list.add(holder.value());
            }
            return list;
        }).orElse(null);
        return new ItemMatch(predicate, items);
    }

    static List<EntityType<?>> entityTypes(JsonElement json, DynamicOps<JsonElement> ops) {
        List<EntityType<?>> types = new ArrayList<>();
        for (Holder<EntityType<?>> holder : HolderSets.entityTypes().parse(ops, json).getOrThrow(IllegalArgumentException::new)) {
            types.add(holder.value());
        }
        return types;
    }

    static int blockLight(String id) {
        Identifier key = Identifier.parse(id);
        return BuiltInRegistries.BLOCK.getOptional(key)
                .orElseThrow(() -> new IllegalArgumentException("unknown block " + key))
                .defaultBlockState().getLightEmission();
    }
}
