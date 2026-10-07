package dev.wick.client.gui;

import dev.wick.client.LightTracker;
import dev.wick.client.WickClient;
import dev.wick.config.WickConfig;
import dev.wick.core.UpdateSpeed;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.config.GuiSlider;
import org.lwjgl.input.Keyboard;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class WickSettingsScreen extends GuiScreen {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 20;
    private static final int GAP = 10;

    private final GuiScreen lastScreen;
    private final Map<GuiButton, String> tooltips = new IdentityHashMap<>();
    private final Map<GuiButton, Runnable> actions = new IdentityHashMap<>();
    private final Map<Integer, String> headers = new java.util.LinkedHashMap<>();
    private int y;

    public WickSettingsScreen(GuiScreen lastScreen) {
        this.lastScreen = lastScreen;
    }

    @Override
    public void initGui() {
        WickConfig config = WickClient.config();
        buttonList.clear();
        tooltips.clear();
        actions.clear();
        headers.clear();
        y = Math.max(28, height / 2 - 112);

        addRow(
                toggle("wick.options.enabled", config::enabled, config::setEnabled),
                tip(button(WIDTH, I18n.format("wick.options.keys"),
                        () -> mc.displayGuiScreen(new GuiControls(this, mc.gameSettings))), "wick.options.keys.tooltip"));

        header("wick.options.section.sources");
        addRow(
                toggle("wick.options.held_items", config::heldItems, config::setHeldItems),
                toggle("wick.options.others", config::others, config::setOthers));
        addRow(
                toggle("wick.options.dropped_items", config::droppedItems, config::setDroppedItems),
                toggle("wick.options.burning", config::burning, config::setBurning));
        addRow(
                toggle("wick.options.glowing_mobs", config::glowingMobs, config::setGlowingMobs),
                toggle("wick.options.water_sensitive", config::waterSensitive, config::setWaterSensitive));
        addRow(
                toggle("wick.options.walls", config::wallsBlockLight, config::setWallsBlockLight),
                toggle("wick.options.enchanted_glow", config::enchantedGlow, config::setEnchantedGlow));

        header("wick.options.section.performance");
        addRow(
                updatesButton(config),
                slider("wick.options.range", WickConfig.MIN_RANGE, WickConfig.MAX_RANGE, 8, config.range(),
                        " " + I18n.format("wick.options.range.value", "").trim(), config::setRange));
        addRow(
                slider("wick.options.max_sources", WickConfig.MIN_SOURCES, WickConfig.MAX_SOURCES, 4,
                        config.maxSources(), "", config::setMaxSources),
                tip(button(WIDTH, I18n.format("wick.options.reset"), () -> {
                    config.resetToDefaults();
                    mc.displayGuiScreen(new WickSettingsScreen(lastScreen));
                }), "wick.options.reset.tooltip"));

        y += 4;
        addRow(button(200, I18n.format("gui.done"), () -> mc.displayGuiScreen(lastScreen)));
    }

    private void header(String key) {
        y += 2;
        headers.put(y, I18n.format(key));
        y += 12;
    }

    private void addRow(GuiButton... buttons) {
        int total = 0;
        for (GuiButton button : buttons) {
            total += button.width;
        }
        int x = (width - total - GAP * (buttons.length - 1)) / 2;
        for (GuiButton button : buttons) {
            button.id = buttonList.size();
            button.x = x;
            button.y = y;
            buttonList.add(button);
            x += button.width + GAP;
        }
        y += 22;
    }

    private GuiButton button(int buttonWidth, String label, Runnable action) {
        GuiButton button = new GuiButton(0, 0, 0, buttonWidth, HEIGHT, label);
        actions.put(button, action);
        return button;
    }

    private GuiButton tip(GuiButton button, String key) {
        tooltips.put(button, I18n.format(key));
        return button;
    }

    private GuiButton toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        GuiButton[] holder = new GuiButton[1];
        holder[0] = button(WIDTH, toggleLabel(key, getter.getAsBoolean()), () -> {
            setter.accept(!getter.getAsBoolean());
            holder[0].displayString = toggleLabel(key, getter.getAsBoolean());
        });
        return tip(holder[0], key + ".tooltip");
    }

    private static String toggleLabel(String key, boolean on) {
        return I18n.format(key) + ": " + I18n.format(on ? "options.on" : "options.off");
    }

    private GuiButton updatesButton(WickConfig config) {
        GuiButton[] holder = new GuiButton[1];
        holder[0] = button(WIDTH, updatesLabel(config.updates()), () -> {
            UpdateSpeed[] values = UpdateSpeed.values();
            config.setUpdates(values[(config.updates().ordinal() + 1) % values.length]);
            holder[0].displayString = updatesLabel(config.updates());
        });
        return tip(holder[0], "wick.options.updates.tooltip");
    }

    private static String updatesLabel(UpdateSpeed speed) {
        return I18n.format("wick.options.updates") + ": " + I18n.format("wick.options.updates." + speed.id());
    }

    private GuiButton slider(String key, int min, int max, int step, int initial, String suffix, IntConsumer onChange) {
        GuiSlider slider = new GuiSlider(0, 0, 0, WIDTH, HEIGHT, I18n.format(key) + ": ", suffix, min, max, initial,
                false, true, changed -> {
                    int snapped = Math.max(min, Math.min(max, min + Math.round((float) (changed.getValue() - min) / step) * step));
                    if (snapped != changed.getValueInt() || changed.getValue() != snapped) {
                        changed.setValue(snapped);
                        changed.updateSlider();
                    }
                    onChange.accept(snapped);
                });
        return tip(slider, key + ".tooltip");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        Runnable action = actions.get(button);
        if (action != null) {
            action.run();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(lastScreen);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, I18n.format("wick.options.title"), width / 2, 12, 0xFFFFFF);
        for (Map.Entry<Integer, String> header : headers.entrySet()) {
            drawCenteredString(fontRenderer, header.getValue(), width / 2, header.getKey(), 0xFFFFFF);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (Map.Entry<GuiButton, String> entry : tooltips.entrySet()) {
            GuiButton button = entry.getKey();
            if (button.visible && mouseX >= button.x && mouseX < button.x + button.width
                    && mouseY >= button.y && mouseY < button.y + button.height) {
                drawHoveringText(fontRenderer.listFormattedStringToWidth(entry.getValue(), 220), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public void onGuiClosed() {
        WickClient.saveConfig();
        LightTracker.refresh(mc);
    }
}
