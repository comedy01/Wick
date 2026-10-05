package dev.wick.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class DataCodecs {
    record ItemMatch(ItemPredicate predicate, List<Item> items) {
        boolean test(ItemStack stack) {
            return predicate.matches(stack);
        }
    }

    private DataCodecs() {
    }

    static DynamicOps<JsonElement> ops(ClientLevel level) {
        return RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    }

    private static <T> T orThrow(DataResult<T> result) {
        return result.getOrThrow(false, message -> {
            throw new IllegalArgumentException(message);
        });
    }

    private static JsonElement oldPredicate(JsonElement json) {
        if (!json.isJsonObject() || !json.getAsJsonObject().has("items")) {
            return json;
        }
        JsonObject copy = json.getAsJsonObject().deepCopy();
        JsonElement items = copy.get("items");
        if (items.isJsonPrimitive()) {
            String value = items.getAsString();
            copy.remove("items");
            if (value.startsWith("#")) {
                copy.addProperty("tag", value.substring(1));
            } else {
                JsonArray list = new JsonArray();
                list.add(value);
                copy.add("items", list);
            }
        }
        return copy;
    }

    static ItemMatch itemMatch(JsonElement json, DynamicOps<JsonElement> ops) {
        ItemPredicate predicate = ItemPredicate.fromJson(oldPredicate(json));
        if (predicate == ItemPredicate.ANY) {
            throw new IllegalArgumentException("empty match");
        }
        return new ItemMatch(predicate, null);
    }

    static List<EntityType<?>> entityTypes(JsonElement json, DynamicOps<JsonElement> ops) {
        List<EntityType<?>> types = new ArrayList<>();
        for (Holder<EntityType<?>> holder : orThrow(HolderSets.entityTypes().parse(ops, json))) {
            types.add(holder.value());
        }
        return types;
    }

    static int blockLight(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            throw new IllegalArgumentException("bad block id " + id);
        }
        return BuiltInRegistries.BLOCK.getOptional(key)
                .orElseThrow(() -> new IllegalArgumentException("unknown block " + key))
                .defaultBlockState().getLightEmission();
    }
}
