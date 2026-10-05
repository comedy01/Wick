package dev.wick.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wick.config.WickConfig;
import net.minecraft.client.KeyMapping;

import java.nio.file.Path;

public final class WickClient {
    public static final String MOD_ID = "wick";

    private static WickConfig config = new WickConfig();
    private static Path configPath;
    private static KeyMapping toggleKey;

    private WickClient() {
    }

    public static void init(Path configDir) {
        configPath = configDir.resolve(WickConfig.FILE_NAME);
        config = WickConfig.load(configPath);
    }

    public static KeyMapping createToggleKey() {
        return KeyFactory.create("key.wick.toggle", InputConstants.UNKNOWN.getValue());
    }

    public static void setToggleKey(KeyMapping key) {
        toggleKey = key;
    }

    public static KeyMapping toggleKey() {
        return toggleKey;
    }

    public static WickConfig config() {
        return config;
    }

    public static void saveConfig() {
        config.saveQuietly(configPath);
    }
}
