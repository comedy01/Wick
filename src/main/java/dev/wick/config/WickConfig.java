package dev.wick.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.wick.core.UpdateSpeed;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class WickConfig {
    public static final String FILE_NAME = "wick.json";

    public static final int MIN_RANGE = 16;
    public static final int MAX_RANGE = 128;
    public static final int MIN_SOURCES = 4;
    public static final int MAX_SOURCES = 128;

    private static final Logger LOGGER = LogManager.getLogger("wick");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean enabled = true;
    private boolean heldItems = true;
    private boolean others = true;
    private boolean droppedItems = true;
    private boolean burning = true;
    private boolean glowingMobs = true;
    private boolean waterSensitive = true;
    private boolean wallsBlockLight = true;
    private boolean enchantedGlow = false;
    private UpdateSpeed updates = UpdateSpeed.REALTIME;
    private int range = 64;
    private int maxSources = 48;
    private Map<String, Integer> items = new LinkedHashMap<>();

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public boolean heldItems() {
        return heldItems;
    }

    public void setHeldItems(boolean value) {
        heldItems = value;
    }

    public boolean others() {
        return others;
    }

    public void setOthers(boolean value) {
        others = value;
    }

    public boolean droppedItems() {
        return droppedItems;
    }

    public void setDroppedItems(boolean value) {
        droppedItems = value;
    }

    public boolean burning() {
        return burning;
    }

    public void setBurning(boolean value) {
        burning = value;
    }

    public boolean glowingMobs() {
        return glowingMobs;
    }

    public void setGlowingMobs(boolean value) {
        glowingMobs = value;
    }

    public boolean waterSensitive() {
        return waterSensitive;
    }

    public void setWaterSensitive(boolean value) {
        waterSensitive = value;
    }

    public boolean wallsBlockLight() {
        return wallsBlockLight;
    }

    public void setWallsBlockLight(boolean value) {
        wallsBlockLight = value;
    }

    public boolean enchantedGlow() {
        return enchantedGlow;
    }

    public void setEnchantedGlow(boolean value) {
        enchantedGlow = value;
    }

    public UpdateSpeed updates() {
        return updates;
    }

    public void setUpdates(UpdateSpeed value) {
        updates = value == null ? UpdateSpeed.REALTIME : value;
    }

    public int range() {
        return range;
    }

    public void setRange(int value) {
        range = clamp(value, MIN_RANGE, MAX_RANGE);
    }

    public int maxSources() {
        return maxSources;
    }

    public void setMaxSources(int value) {
        maxSources = clamp(value, MIN_SOURCES, MAX_SOURCES);
    }

    public int itemLevel(String id) {
        Integer level = items.get(id);
        return level == null ? -1 : level;
    }

    public void setItemLevel(String id, int level) {
        items.put(normalizeId(id), clamp(level, 0, 15));
    }

    public void clearItemLevels() {
        items.clear();
    }

    public void resetToDefaults() {
        Map<String, Integer> keep = items;
        copyFrom(new WickConfig());
        items = keep;
    }

    private void copyFrom(WickConfig other) {
        enabled = other.enabled;
        heldItems = other.heldItems;
        others = other.others;
        droppedItems = other.droppedItems;
        burning = other.burning;
        glowingMobs = other.glowingMobs;
        waterSensitive = other.waterSensitive;
        wallsBlockLight = other.wallsBlockLight;
        enchantedGlow = other.enchantedGlow;
        updates = other.updates;
        range = other.range;
        maxSources = other.maxSources;
        items = new LinkedHashMap<>(other.items);
    }

    private void sanitize() {
        setUpdates(updates);
        setRange(range);
        setMaxSources(maxSources);
        Map<String, Integer> clean = new LinkedHashMap<>();
        if (items != null) {
            items.forEach((id, level) -> {
                if (id != null && !id.isBlank() && level != null) {
                    clean.put(normalizeId(id), clamp(level, 0, 15));
                }
            });
        }
        items = clean;
    }

    static String normalizeId(String id) {
        String clean = id.trim().toLowerCase(Locale.ROOT);
        return clean.indexOf(':') < 0 ? "minecraft:" + clean : clean;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static WickConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            WickConfig fresh = new WickConfig();
            fresh.saveQuietly(file);
            return fresh;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            WickConfig loaded = GSON.fromJson(reader, WickConfig.class);
            if (loaded == null) {
                throw new JsonParseException("config file is empty");
            }
            loaded.sanitize();
            return loaded;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}; using defaults. {}", file, e.toString());
            moveAside(file);
            WickConfig fresh = new WickConfig();
            fresh.saveQuietly(file);
            return fresh;
        }
    }

    public void save(Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Files.write(temp, (GSON.toJson(this) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void saveQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            save(file);
        } catch (IOException e) {
            LOGGER.warn("Could not save {}: {}", file, e.toString());
        }
    }

    private static void moveAside(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not back up unreadable config {}: {}", file, e.toString());
        }
    }
}
