package dev.wick.client.gui;

import dev.wick.client.LightTracker;
import dev.wick.client.Texts;
import dev.wick.client.WickClient;
import dev.wick.config.WickConfig;
import dev.wick.core.Choice;
import dev.wick.core.UpdateSpeed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

public final class WickSettingsScreen extends WickOptionsScreen {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 20;

    public WickSettingsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Texts.translatable("wick.options.title"));
    }

    @Override
    protected void addOptions() {
        WickConfig config = WickClient.config();

        addRow(toggle("wick.options.enabled", config::enabled, config::setEnabled), keyBindsButton());

        addHeader(Texts.translatable("wick.options.section.sources"));
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

        addHeader(Texts.translatable("wick.options.section.performance"));
        addRow(
                cycle("wick.options.updates", UpdateSpeed.values(), config::updates, config::setUpdates),
                slider("wick.options.range", WickConfig.MIN_RANGE, WickConfig.MAX_RANGE, 8, config.range(),
                        value -> Texts.translatable("wick.options.range.value", (int) value).getString(), value -> config.setRange((int) value)));
        addRow(
                slider("wick.options.max_sources", WickConfig.MIN_SOURCES, WickConfig.MAX_SOURCES, 4,
                        config.maxSources(), value -> Integer.toString((int) value),
                        value -> config.setMaxSources((int) value)),
                resetButton(config));
    }

    @Override
    public void removed() {
        super.removed();
        WickClient.saveConfig();
        LightTracker.refresh(Minecraft.getInstance());
    }

    private AbstractWidget keyBindsButton() {
        return tooltip(button(Texts.translatable("wick.options.keys"), WIDTH,
                        button -> ScreenOpener.open(minecraft, KeyBinds.screen(this, options))),
                Texts.translatable("wick.options.keys.tooltip"));
    }

    private AbstractWidget resetButton(WickConfig config) {
        return tooltip(button(Texts.translatable("wick.options.reset"), WIDTH, button -> {
            config.resetToDefaults();
            ScreenOpener.open(minecraft, new WickSettingsScreen(lastScreen, options));
        }), Texts.translatable("wick.options.reset.tooltip"));
    }

    private AbstractWidget toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        return tooltip(button(toggleLabel(key, getter.getAsBoolean()), WIDTH, button -> {
            setter.accept(!getter.getAsBoolean());
            button.setMessage(toggleLabel(key, getter.getAsBoolean()));
        }), Texts.translatable(key + ".tooltip"));
    }

    private static Component toggleLabel(String key, boolean on) {
        Component state = Texts.translatable(on ? "options.on" : "options.off");
        return Texts.translatable("options.generic_value", Texts.translatable(key), state);
    }

    private <T extends Enum<T> & Choice> AbstractWidget cycle(
            String key, T[] values, Supplier<T> getter, Consumer<T> setter) {
        return tooltip(button(cycleLabel(key, getter.get()), WIDTH, button -> {
            T next = values[(getter.get().ordinal() + 1) % values.length];
            setter.accept(next);
            button.setMessage(cycleLabel(key, getter.get()));
        }), Texts.translatable(key + ".tooltip"));
    }

    private static Component cycleLabel(String key, Choice value) {
        return Texts.translatable("options.generic_value", Texts.translatable(key),
                Texts.translatable(key + "." + value.id()));
    }

    private AbstractWidget slider(String key, double min, double max, double step, double initial,
                                  DoubleFunction<String> format, DoubleConsumer onChange) {
        return tooltip(new StepSlider(key, min, max, step, initial, format, onChange),
                Texts.translatable(key + ".tooltip"));
    }

    private static final class StepSlider extends AbstractSliderButton {
        private final String captionKey;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleFunction<String> format;
        private final DoubleConsumer onChange;

        StepSlider(String captionKey, double min, double max, double step, double initial,
                   DoubleFunction<String> format, DoubleConsumer onChange) {
            super(0, 0, WIDTH, HEIGHT, Texts.empty(), 0.0);
            this.captionKey = captionKey;
            this.min = min;
            this.max = max;
            this.step = step;
            this.format = format;
            this.onChange = onChange;
            this.value = (snap(initial) - min) / (max - min);
            updateMessage();
        }

        private double snap(double raw) {
            double clamped = Math.max(min, Math.min(max, raw));
            double snapped = min + Math.round((clamped - min) / step) * step;
            return Math.max(min, Math.min(max, snapped));
        }

        private double current() {
            return snap(min + value * (max - min));
        }

        @Override
        protected void updateMessage() {
            Component shown = Texts.literal(format.apply(current()));
            setMessage(Texts.translatable("options.generic_value", Texts.translatable(captionKey), shown));
        }

        @Override
        protected void applyValue() {
            double snapped = current();
            value = (snapped - min) / (max - min);
            onChange.accept(snapped);
        }
    }
}
