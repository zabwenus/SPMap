package org.zabwenus.mod.map.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.util.Identifier;
import xaero.lib.client.gui.widget.Tooltip;
import xaero.map.gui.GuiTexturedButton;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class SpMapColorButton extends GuiTexturedButton {
    private final IntSupplier colorSupplier;

    public SpMapColorButton(int x, int y, IntSupplier colorSupplier,
                            ButtonWidget.PressAction action, Supplier<Tooltip> tooltip) {
        super(x, y, 20, 20, 0, 0, 1, 1, Identifier.of("map", "gui/xpgui.png"),
            action, tooltip, 256, 256);
        this.colorSupplier = colorSupplier;
    }

    @Override
    public void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX() + 6;
        int y = getY() + 6 - (isHovered() ? 1 : 0);
        int color = colorSupplier.getAsInt();
        context.fill(x, y, x + 9, y + 9, color);
        context.fill(x, y, x + 9, y + 1, 0xFFFFFFFF);
        context.fill(x, y + 8, x + 9, y + 9, 0xFF202020);
        context.fill(x, y, x + 1, y + 9, 0xFFFFFFFF);
        context.fill(x + 8, y, x + 9, y + 9, 0xFF202020);
    }
}
