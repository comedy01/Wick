package dev.wick.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.ToIntFunction;

public final class DataLights implements ResourceManagerReloadListener {
    private static final Logger LOGGER = LogManager.getLogger("wick");
    private static final Gson GSON = new Gson();
    private static final String ITEMS = "dynamiclights/item";
    private static final String ENTITIES = "dynamiclights/entity";

    private record Raw(String file, JsonObject json) {
    }

    private record ItemRule(DataCodecs.ItemMatch match, ToIntFunction<ItemStack> luminance, boolean waterSensitive) {
    }

    private static List<Raw> itemFiles = List.of();
    private static List<Raw> entityFiles = List.of();
    private static boolean registered;
    private static int generation;

    private static int compiledGeneration = -1;
    private static ClientLevel compiledFor;
    private static Map<Item, List<ItemRule>> itemRules = Map.of();
    private static List<ItemRule> anyItemRules = List.of();
    private static Map<EntityType<?>, List<ToIntFunction<Entity>>> entityRules = Map.of();

    private static final DataLights LISTENER = new DataLights();

    private DataLights() {
    }

    public static ResourceManagerReloadListener listener() {
        registered = true;
        return LISTENER;
    }

    static void update(Minecraft mc, ClientLevel level) {
        if (!registered) {
            registered = true;
            if (mc.getResourceManager() instanceof ReloadableResourceManager manager) {
                try {
                    manager.registerReloadListener(LISTENER);
                } catch (UnsupportedOperationException e) {
                    LOGGER.warn("Could not watch resource reloads; light files are read once");
                }
            }
            load(mc.getResourceManager());
        }
        if (level != compiledFor || generation != compiledGeneration) {
            compile(level);
        }
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        load(manager);
    }

    private static void load(ResourceManager manager) {
        itemFiles = read(manager, ITEMS);
        entityFiles = read(manager, ENTITIES);
        generation++;
    }

    private static List<Raw> read(ResourceManager manager, String directory) {
        List<Raw> found = new ArrayList<>();
        for (Map.Entry<String, Callable<Reader>> entry : ResourceFiles.json(manager, directory)) {
            try (Reader reader = entry.getValue().call()) {
                JsonElement json = GSON.fromJson(reader, JsonElement.class);
                if (json.isJsonObject()) {
                    found.add(new Raw(entry.getKey(), json.getAsJsonObject()));
                } else {
                    LOGGER.warn("Skipping light file {}: not a JSON object", entry.getKey());
                }
            } catch (Exception e) {
                LOGGER.warn("Skipping light file {}: {}", entry.getKey(), e.toString());
            }
        }
        return found;
    }

    private static void compile(ClientLevel level) {
        compiledFor = level;
        compiledGeneration = generation;
        itemRules = Map.of();
        anyItemRules = List.of();
        entityRules = Map.of();
        if (level == null || (itemFiles.isEmpty() && entityFiles.isEmpty())) {
            return;
        }
        DynamicOps<JsonElement> ops = DataCodecs.ops(level);
        Map<Item, List<ItemRule>> items = new Reference2ObjectOpenHashMap<>();
        List<ItemRule> any = new ArrayList<>();
        int itemsLoaded = 0;
        int entitiesLoaded = 0;
        int skipped = 0;
        for (Raw raw : itemFiles) {
            try {
                ItemRule rule = itemRule(raw.json(), ops);
                if (rule.match().items() != null) {
                    for (Item item : rule.match().items()) {
                        items.computeIfAbsent(item, key -> new ArrayList<>(1)).add(rule);
                    }
                } else {
                    any.add(rule);
                }
                itemsLoaded++;
            } catch (RuntimeException e) {
                skipped++;
                skippedFile(raw, e);
            }
        }
        Map<EntityType<?>, List<ToIntFunction<Entity>>> entities = new Reference2ObjectOpenHashMap<>();
        for (Raw raw : entityFiles) {
            try {
                JsonObject match = object(raw.json(), "match");
                for (Map.Entry<String, JsonElement> entry : match.entrySet()) {
                    if (!entry.getKey().equals("type")) {
                        throw new IllegalArgumentException("Wick only understands \"type\" in an entity match, not \"" + entry.getKey() + "\"");
                    }
                }
                List<EntityType<?>> types = DataCodecs.entityTypes(required(match, "type"), ops);
                ToIntFunction<Entity> luminance = entityLuminance(required(raw.json(), "luminance"));
                for (EntityType<?> type : types) {
                    entities.computeIfAbsent(type, key -> new ArrayList<>(1)).add(luminance);
                }
                entitiesLoaded++;
            } catch (RuntimeException e) {
                skipped++;
                skippedFile(raw, e);
            }
        }
        itemRules = items;
        anyItemRules = any;
        entityRules = entities;
        LOGGER.info("Loaded {} item and {} entity light files ({} skipped)", itemsLoaded, entitiesLoaded, skipped);
    }

    private static void skippedFile(Raw raw, RuntimeException e) {
        LOGGER.debug("Skipping light file {}: {}", raw.file(), e.getMessage());
    }

    private static ItemRule itemRule(JsonObject json, DynamicOps<JsonElement> ops) {
        DataCodecs.ItemMatch match = DataCodecs.itemMatch(required(json, "match"), ops);
        ToIntFunction<ItemStack> luminance = itemLuminance(required(json, "luminance"));
        boolean water = json.has("water_sensitive") && json.get("water_sensitive").getAsBoolean();
        return new ItemRule(match, luminance, water);
    }

    private static ToIntFunction<ItemStack> itemLuminance(JsonElement json) {
        if (json.isJsonPrimitive()) {
            int value = clamp(json.getAsInt());
            return stack -> value;
        }
        JsonObject object = json.getAsJsonObject();
        switch (type(object)) {
            case "block" -> {
                int value = DataCodecs.blockLight(required(object, "block").getAsString());
                return stack -> value;
            }
            case "block_self" -> {
                return stack -> stack.getItem() instanceof BlockItem ? ItemLights.builtIn(stack, stack.getItem(), "") : 0;
            }
            default -> throw new IllegalArgumentException("unknown item luminance " + type(object));
        }
    }

    private static ToIntFunction<Entity> entityLuminance(JsonElement json) {
        if (json.isJsonPrimitive()) {
            int value = clamp(json.getAsInt());
            return entity -> value;
        }
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            List<ToIntFunction<Entity>> parts = new ArrayList<>(array.size());
            for (JsonElement part : array) {
                parts.add(entityLuminance(part));
            }
            return entity -> {
                int best = 0;
                for (ToIntFunction<Entity> part : parts) {
                    best = Math.max(best, part.applyAsInt(entity));
                }
                return best;
            };
        }
        JsonObject object = json.getAsJsonObject();
        switch (type(object)) {
            case "value" -> {
                int value = clamp(required(object, "value").getAsInt());
                return entity -> value;
            }
            case "water_sensitive" -> {
                int dry = clamp(required(object, "out_of_water").getAsInt());
                int wet = clamp(required(object, "in_water").getAsInt());
                return entity -> entity.isInWater() ? wet : dry;
            }
            case "wet_sensitive" -> {
                int dry = clamp(required(object, "dry").getAsInt());
                int wet = clamp(required(object, "wet").getAsInt());
                return entity -> entity.isInWaterOrRain() ? wet : dry;
            }
            default -> throw new IllegalArgumentException("unknown entity luminance " + type(object));
        }
    }

    private static String type(JsonObject object) {
        String type = required(object, "type").getAsString();
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1);
    }

    private static JsonElement required(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing \"" + key + "\"");
        }
        return value;
    }

    private static JsonObject object(JsonObject object, String key) {
        return required(object, key).getAsJsonObject();
    }

    private static int clamp(int level) {
        return Math.max(0, Math.min(15, level));
    }

    static int itemLevel(ItemStack stack, boolean wet) {
        List<ItemRule> rules = itemRules.get(stack.getItem());
        if (rules == null && anyItemRules.isEmpty()) {
            return -1;
        }
        int best = -1;
        best = match(rules, stack, wet, best);
        best = match(anyItemRules, stack, wet, best);
        return best;
    }

    private static int match(List<ItemRule> rules, ItemStack stack, boolean wet, int best) {
        if (rules == null) {
            return best;
        }
        for (ItemRule rule : rules) {
            if (rule.match().test(stack)) {
                best = Math.max(best, wet && rule.waterSensitive() ? 0 : rule.luminance().applyAsInt(stack));
            }
        }
        return best;
    }

    static int entityLevel(Entity entity) {
        List<ToIntFunction<Entity>> rules = entityRules.get(entity.getType());
        if (rules == null) {
            return -1;
        }
        int best = 0;
        for (ToIntFunction<Entity> rule : rules) {
            best = Math.max(best, rule.applyAsInt(entity));
        }
        return best;
    }

    public static String summary() {
        return itemRules.size() + anyItemRules.size() + " items, " + entityRules.size() + " entity types";
    }
}
