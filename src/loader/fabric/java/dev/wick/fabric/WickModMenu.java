package dev.wick.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.wick.client.gui.WickSettingsScreen;
import net.minecraft.client.Minecraft;

public final class WickModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new WickSettingsScreen(parent, Minecraft.getInstance().options);
    }
}
