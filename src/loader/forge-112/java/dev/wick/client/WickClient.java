package dev.wick.client;

import dev.wick.config.WickConfig;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.nio.file.Path;

public final class WickClient {
    public static final String MOD_ID = "wick";

    private static WickConfig config = new WickConfig();
    private static Path configPath;
    private static KeyBinding toggleKey;

    private WickClient() {
    }

    public static void init(Path configDir) {
        configPath = configDir.resolve(WickConfig.FILE_NAME);
        config = WickConfig.load(configPath);
    }

    public static KeyBinding createToggleKey() {
        toggleKey = new KeyBinding("key.wick.toggle", Keyboard.KEY_NONE, "key.categories.wick.main");
        return toggleKey;
    }

    public static KeyBinding toggleKey() {
        return toggleKey;
    }

    public static WickConfig config() {
        return config;
    }

    public static void saveConfig() {
        config.saveQuietly(configPath);
    }
}
