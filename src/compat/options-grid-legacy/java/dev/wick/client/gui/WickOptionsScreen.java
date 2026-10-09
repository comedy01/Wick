package dev.wick.client.gui;

import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public abstract class WickOptionsScreen extends OptionsSubScreen {
    private static final int ROW = 24;
    private static final int TOP = 32;
    private static final int BOTTOM = 36;

    private final List<Row> rowWidgets = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private int rows;
    private int scroll;

    protected WickOptionsScreen(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    protected abstract void addOptions();

    @Override
    protected void init() {
        rowWidgets.clear();
        headers.clear();
        rows = 0;
        addOptions();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        layout();
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 100, height - 27, 200, 20)
                .build());
    }

    protected static Button button(Component label, int width, Button.OnPress onPress) {
        return Button.builder(label, onPress).width(width).build();
    }

    protected <T extends AbstractWidget> T tooltip(T widget, Component text) {
        widget.setTooltip(Tooltip.create(text));
        return widget;
    }

    protected void addHeader(Component text) {
        headers.add(new Header(text, rows));
        rows++;
    }

    protected void addRow(AbstractWidget left, AbstractWidget right) {
        rowWidgets.add(new Row(left, rows, -155));
        addRenderableWidget(left);
        if (right != null) {
            rowWidgets.add(new Row(right, rows, 5));
            addRenderableWidget(right);
        }
        rows++;
    }

    private int maxScroll() {
        return Math.max(0, rows * ROW - (height - TOP - BOTTOM));
    }

    private int rowY(int row) {
        return TOP + row * ROW - scroll;
    }

    private boolean rowVisible(int row) {
        int y = rowY(row);
        return y >= TOP - 2 && y + 20 <= height - BOTTOM + 2;
    }

    private void layout() {
        for (Row row : rowWidgets) {
            row.widget.setPosition(width / 2 + row.offset, rowY(row.row));
            row.widget.visible = rowVisible(row.row);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(delta * ROW)));
        layout();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        for (Header header : headers) {
            if (rowVisible(header.row)) {
                graphics.drawCenteredString(font, header.text, width / 2,
                        rowY(header.row) + (20 - font.lineHeight) / 2 + 1, 0xFFFFFF);
            }
        }
    }

    private static final class Row {
        final AbstractWidget widget;
        final int row;
        final int offset;

        Row(AbstractWidget widget, int row, int offset) {
            this.widget = widget;
            this.row = row;
            this.offset = offset;
        }
    }

    private static final class Header {
        final Component text;
        final int row;

        Header(Component text, int row) {
            this.text = text;
            this.row = row;
        }
    }
}
