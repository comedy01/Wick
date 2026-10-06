package dev.wick.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.SerializationTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DataCodecs {
    record ItemMatch(ItemPredicate predicate, List<Item> items) {
        boolean test(ItemStack stack) {
            return predicate.matches(stack);
        }
    }

    private DataCodecs() {
    }

    static DynamicOps<JsonElement> ops(ClientLevel level) {
        return JsonOps.INSTANCE;
    }

    static ItemMatch itemMatch(JsonElement json, DynamicOps<JsonElement> ops) {
        JsonObject rest = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            rest.add(entry.getKey(), entry.getValue());
        }
        JsonElement listed = rest.remove("items");
        List<Item> items = null;
        if (listed != null && listed.isJsonPrimitive() && listed.getAsString().startsWith("#")) {
            rest.addProperty("tag", listed.getAsString().substring(1));
        } else if (listed != null) {
            items = new ArrayList<>();
            for (JsonElement element : listed.isJsonArray() ? listed.getAsJsonArray() : single(listed)) {
                items.add(item(element.getAsString()));
            }
        }
        ItemPredicate predicate = ItemPredicate.fromJson(rest);
        if (predicate == ItemPredicate.ANY && items == null) {
            throw new IllegalArgumentException("empty match");
        }
        return new ItemMatch(predicate, items);
    }

    private static JsonArray single(JsonElement element) {
        JsonArray array = new JsonArray();
        array.add(element);
        return array;
    }

    private static Item item(String value) {
        ResourceLocation key = id(value);
        return Registry.ITEM.getOptional(key)
                .orElseThrow(() -> new IllegalArgumentException("unknown item " + key));
    }

    static List<EntityType<?>> entityTypes(JsonElement json, DynamicOps<JsonElement> ops) {
        List<EntityType<?>> types = new ArrayList<>();
        if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) {
                types.add(entityType(element.getAsString()));
            }
            return types;
        }
        String value = json.getAsString();
        if (value.startsWith("#")) {
            Tag<EntityType<?>> tag = SerializationTags.getInstance().getEntityTypes().getTag(id(value.substring(1)));
            if (tag == null) {
                throw new IllegalArgumentException("unknown entity type tag " + value);
            }
            types.addAll(tag.getValues());
        } else {
            types.add(entityType(value));
        }
        return types;
    }

    private static EntityType<?> entityType(String value) {
        ResourceLocation key = id(value);
        return Registry.ENTITY_TYPE.getOptional(key)
                .orElseThrow(() -> new IllegalArgumentException("unknown entity type " + key));
    }

    private static ResourceLocation id(String value) {
        ResourceLocation key = ResourceLocation.tryParse(value);
        if (key == null) {
            throw new IllegalArgumentException("bad id " + value);
        }
        return key;
    }

    static int blockLight(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            throw new IllegalArgumentException("bad block id " + id);
        }
        return Registry.BLOCK.getOptional(key)
                .orElseThrow(() -> new IllegalArgumentException("unknown block " + key))
                .defaultBlockState().getLightEmission();
    }
}
