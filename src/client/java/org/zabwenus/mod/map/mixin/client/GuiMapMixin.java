package org.zabwenus.mod.map.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zabwenus.mod.map.client.MapDrawing;
import org.zabwenus.mod.map.client.SpMapColorButton;
import xaero.map.gui.GuiMap;
import xaero.map.gui.GuiTexturedButton;
import xaero.lib.client.gui.widget.Tooltip;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetMouseButton;

@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapMixin {
    @Shadow private static double destScale;
    @Shadow private double cameraX;
    @Shadow private double cameraZ;
    @Shadow private double scale;
    @Shadow private double screenScale;
    @Shadow private int mouseBlockPosX;
    @Shadow private int mouseBlockPosZ;
    @Shadow private ButtonWidget attachedCameraButton;

    @Shadow public abstract <T extends Element & Drawable & Selectable> T addButton(T button);
    @Shadow public abstract <T extends Element & Drawable & Selectable> T addDrawableChild(T child);

    @Unique private static final Identifier spmap$guiTexture = Identifier.of("map", "gui/xpgui.png");
    @Unique private ButtonWidget spmap$menuButton;
    @Unique private ButtonWidget spmap$drawButton;
    @Unique private ButtonWidget spmap$gridButton;
    @Unique private ButtonWidget spmap$chunkButton;
    @Unique private ButtonWidget spmap$areaButton;
    @Unique private ButtonWidget spmap$textButton;
    @Unique private ButtonWidget spmap$colorButton;
    @Unique private ButtonWidget spmap$coordsButton;
    @Unique private ButtonWidget spmap$exportButton;
    @Unique private ButtonWidget spmap$removeCoordinatesButton;
    @Unique private ButtonWidget spmap$undoButton;
    @Unique private TextFieldWidget spmap$textField;
    @Unique private boolean spmap$gridVisible;
    @Unique private boolean spmap$toolsVisible;
    @Unique private boolean spmap$drawing;
    @Unique private MapDrawing.Type spmap$mode = MapDrawing.Type.LINE;
    @Unique private int spmap$color = 0xB4FF3B30;
    @Unique private int spmap$startX;
    @Unique private int spmap$startZ;
    @Unique private boolean spmap$hasStart;
    @Unique private int spmap$textX;
    @Unique private int spmap$textZ;
    @Unique private MapDrawing.Area spmap$previewArea;
    @Unique private String spmap$notice;
    @Unique private long spmap$noticeUntil;
    @Unique private boolean spmap$leftMouseDown;
    @Unique private boolean spmap$rightMouseDown;
    @Unique private int spmap$lastChunkX = Integer.MIN_VALUE;
    @Unique private int spmap$lastChunkZ = Integer.MIN_VALUE;
    @Unique private int spmap$lastErasedChunkX = Integer.MIN_VALUE;
    @Unique private int spmap$lastErasedChunkZ = Integer.MIN_VALUE;

    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void spmap$init(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        MapDrawing.ensureLoaded(client);
        spmap$hasStart = false;
        spmap$leftMouseDown = false;
        spmap$rightMouseDown = false;
        spmap$resetChunkTracking();
        int menuX = 0;
        int menuY = attachedCameraButton == null ? Math.max(0, ((Screen) (Object) this).height - 140)
            : attachedCameraButton.getY() - 20;
        int toolX = menuX + 20;
        int secondaryX = menuX + 40;
        int tertiaryX = menuX + 60;

        spmap$menuButton = addButton(spmap$iconButton(menuX, menuY, 47, 0,
            button -> spmap$toggleTools(), "Открыть инструменты рисования"));
        spmap$drawButton = addButton(spmap$iconButton(toolX, menuY, 65, 0,
            button -> spmap$toggleDrawing(), "Включить режим рисования"));
        spmap$gridButton = addButton(spmap$iconButton(toolX, menuY + 20, 101, 0,
            button -> spmap$toggleGrid(), "Показать сетку чанков"));
        spmap$chunkButton = addButton(spmap$iconButton(toolX, menuY + 40, 82, 0,
            button -> spmap$startMode(MapDrawing.Type.CHUNK, "Выделение чанков: зажмите левую кнопку мыши"),
            "Выделять чанки"));
        spmap$areaButton = addButton(spmap$iconButton(toolX, menuY + 60, 137, 19,
            button -> spmap$startMode(MapDrawing.Type.AREA, "Выделение территории: протяните между угловыми чанками"),
            "Выделить территорию"));
        spmap$textButton = addButton(spmap$iconButton(toolX, menuY + 80, 119, 0,
            button -> spmap$startMode(MapDrawing.Type.TEXT, "Добавить текст на карту"),
            "Добавить текст"));
        spmap$colorButton = addButton(new SpMapColorButton(secondaryX, menuY,
            () -> spmap$color, button -> spmap$cycleColor(),
            () -> new Tooltip(Text.literal("Изменить цвет рисования"))));
        spmap$coordsButton = addButton(spmap$iconButton(secondaryX, menuY + 20, 135, 0,
            button -> spmap$startMode(MapDrawing.Type.COORDINATE,
                "Нажмите на карту, чтобы поставить координату"),
            "Поставить координату"));
        spmap$exportButton = addButton(spmap$iconButton(tertiaryX, menuY, 18, 0,
            button -> {
                String path = MapDrawing.exportCoordinates(client);
                spmap$showNotice(path == null
                    ? "Не удалось создать файл координат"
                    : "Координаты выгружены в текстовый файл");
            }, "Выгрузить все координаты в текстовый файл"));
        spmap$removeCoordinatesButton = addButton(spmap$iconButton(tertiaryX, menuY + 20, 0, 19,
            button -> spmap$startMode(MapDrawing.Type.REMOVE_COORDINATE,
                "Нажмите ЛКМ по координате, чтобы удалить её"),
            "Удалить координату"));
        spmap$undoButton = addButton(spmap$iconButton(secondaryX, menuY + 40, 0, 19,
            button -> {
                MapDrawing.undo(client);
                spmap$showNotice("Последнее действие отменено");
            }, "Отменить последнее действие"));
        spmap$setToolsVisible(spmap$toolsVisible);

        spmap$textField = addDrawableChild(new TextFieldWidget(
            client.textRenderer, 0, 0, 160, 20, Text.literal("Введите текст")));
        spmap$textField.setVisible(false);
    }

    @Unique
    private void spmap$copyCorners() {
        MinecraftClient client = MinecraftClient.getInstance();
        MapDrawing.Area area = spmap$previewArea != null ? spmap$previewArea : MapDrawing.lastArea();
        if (area == null) {
            spmap$showNotice("Сначала выделите территорию");
            return;
        }
        String corners = MapDrawing.cornerCoordinates(area);
        MapDrawing.addCornerLabels(client, area);
        client.keyboard.setClipboard(corners);
        spmap$showNotice("Координаты углов добавлены и скопированы");
    }

    @Unique
    private void spmap$showNotice(String text) {
        spmap$notice = text;
        spmap$noticeUntil = System.currentTimeMillis() + 5000;
    }

    @Unique
    private GuiTexturedButton spmap$iconButton(int x, int y, int textureX, int textureY,
                                               ButtonWidget.PressAction action, String tooltip) {
        return new GuiTexturedButton(x, y, 20, 20, textureX, textureY, 16, 16,
            spmap$guiTexture, action, () -> new Tooltip(Text.literal(tooltip)), 256, 256);
    }

    @Unique
    private void spmap$toggleTools() {
        spmap$setToolsVisible(!spmap$toolsVisible);
    }

    @Unique
    private void spmap$setToolsVisible(boolean visible) {
        spmap$toolsVisible = visible;
        if (spmap$drawButton != null) spmap$drawButton.visible = visible;
        if (spmap$gridButton != null) spmap$gridButton.visible = visible;
        if (spmap$chunkButton != null) spmap$chunkButton.visible = visible;
        if (spmap$areaButton != null) spmap$areaButton.visible = visible;
        if (spmap$textButton != null) spmap$textButton.visible = visible;
        if (spmap$colorButton != null) spmap$colorButton.visible = visible;
        if (spmap$coordsButton != null) spmap$coordsButton.visible = visible;
        if (spmap$exportButton != null) spmap$exportButton.visible = visible;
        if (spmap$removeCoordinatesButton != null) spmap$removeCoordinatesButton.visible = visible;
        if (spmap$undoButton != null) spmap$undoButton.visible = visible;
    }

    @Unique
    private void spmap$toggleDrawing() {
        spmap$drawing = !spmap$drawing;
        spmap$mode = MapDrawing.Type.LINE;
        spmap$hasStart = false;
        spmap$resetChunkTracking();
        spmap$showNotice(spmap$drawing ? "Рисование включено: ЛКМ рисует, ПКМ стирает"
            : "Рисование выключено");
    }

    @Unique
    private void spmap$startMode(MapDrawing.Type mode, String notice) {
        spmap$drawing = true;
        spmap$mode = mode;
        spmap$hasStart = false;
        spmap$previewArea = null;
        spmap$resetChunkTracking();
        spmap$showNotice(notice);
    }

    @Unique
    private void spmap$toggleGrid() {
        spmap$gridVisible = !spmap$gridVisible;
        spmap$showNotice(spmap$gridVisible ? "Сетка чанков включена" : "Сетка чанков выключена");
    }

    @Unique
    private void spmap$cycleColor() {
        spmap$color = spmap$color == 0xB4FF3B30 ? 0xB43B82F6
            : spmap$color == 0xB43B82F6 ? 0xB43BBF62 : 0xB4FF3B30;
        spmap$resetChunkTracking();
        spmap$showNotice("Цвет рисования изменён");
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void spmap$mouseClicked(Click event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!spmap$drawing || event.button() > 1) {
            return;
        }
        if (spmap$isOnSpmapWidget(event.x(), event.y())) {
            return;
        }
        if (event.button() == 1) {
            spmap$rightMouseDown = true;
            if (spmap$hasStart) {
                spmap$hasStart = false;
                spmap$previewArea = null;
            } else {
                MapDrawing.eraseAt(MinecraftClient.getInstance(), mouseBlockPosX, mouseBlockPosZ);
            }
            cir.setReturnValue(true);
            return;
        }

        spmap$leftMouseDown = true;
        spmap$startX = mouseBlockPosX;
        spmap$startZ = mouseBlockPosZ;
        spmap$hasStart = true;
        if (spmap$mode == MapDrawing.Type.CHUNK) {
            spmap$addCurrentChunk();
            spmap$hasStart = false;
        } else if (spmap$mode == MapDrawing.Type.COORDINATE) {
            MapDrawing.addCoordinateLabel(MinecraftClient.getInstance(), mouseBlockPosX, mouseBlockPosZ);
            spmap$showNotice("Координата добавлена");
            spmap$hasStart = false;
        } else if (spmap$mode == MapDrawing.Type.REMOVE_COORDINATE) {
            boolean removed = MapDrawing.removeCoordinateAt(
                MinecraftClient.getInstance(), mouseBlockPosX, mouseBlockPosZ);
            spmap$showNotice(removed ? "Координата удалена" : "Рядом нет координаты");
            spmap$hasStart = false;
        } else if (spmap$mode == MapDrawing.Type.TEXT) {
            spmap$textX = mouseBlockPosX;
            spmap$textZ = mouseBlockPosZ;
            Screen screen = (Screen) (Object) this;
            spmap$textField.setX(Math.max(4, Math.min((int) event.x() - 80, screen.width - 164)));
            spmap$textField.setY(Math.max(28, Math.min((int) event.y() - 10, screen.height - 24)));
            spmap$textField.setText("");
            spmap$textField.setVisible(true);
            spmap$textField.setFocused(true);
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, remap = true)
    private void spmap$mouseReleased(Click event, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == 0) {
            spmap$leftMouseDown = false;
            spmap$lastChunkX = Integer.MIN_VALUE;
            spmap$lastChunkZ = Integer.MIN_VALUE;
        } else if (event.button() == 1) {
            spmap$rightMouseDown = false;
            spmap$lastErasedChunkX = Integer.MIN_VALUE;
            spmap$lastErasedChunkZ = Integer.MIN_VALUE;
        }
        if (!spmap$drawing) {
            return;
        }
        if (event.button() == 1) {
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == 0
            && (spmap$mode == MapDrawing.Type.CHUNK
                || spmap$mode == MapDrawing.Type.COORDINATE
                || spmap$mode == MapDrawing.Type.REMOVE_COORDINATE)) {
            cir.setReturnValue(true);
            return;
        }
        if (event.button() != 0 || !spmap$hasStart) {
            return;
        }
        if (spmap$isOnSpmapWidget(event.x(), event.y())) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (spmap$mode == MapDrawing.Type.LINE) {
            MapDrawing.addLine(client, new MapDrawing.Line(
                spmap$startX, spmap$startZ, mouseBlockPosX, mouseBlockPosZ, spmap$color));
            spmap$hasStart = false;
        } else if (spmap$mode == MapDrawing.Type.AREA) {
            MapDrawing.Area area = new MapDrawing.Area(
                Math.floorDiv(spmap$startX, 16), Math.floorDiv(spmap$startZ, 16),
                Math.floorDiv(mouseBlockPosX, 16), Math.floorDiv(mouseBlockPosZ, 16), spmap$color);
            MapDrawing.addArea(client, area);
            spmap$previewArea = area;
            spmap$hasStart = false;
            spmap$showNotice(MapDrawing.cornerCoordinates(area));
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, remap = true)
    private void spmap$keyPressed(KeyInput event, CallbackInfoReturnable<Boolean> cir) {
        if ((event.modifiers() & 2) != 0 && event.key() == 90) {
            MapDrawing.undo(MinecraftClient.getInstance());
            cir.setReturnValue(true);
            return;
        }
        if (spmap$textField != null && spmap$textField.isVisible() && event.key() == 257) {
            String value = spmap$textField.getText().trim();
            if (!value.isEmpty()) {
                MapDrawing.addText(MinecraftClient.getInstance(),
                    new MapDrawing.Text(spmap$textX, spmap$textZ, value, 0xFFFFFFFF));
                spmap$showNotice("Текст добавлен");
            }
            spmap$textField.setVisible(false);
            spmap$hasStart = false;
            cir.setReturnValue(true);
            return;
        }
        if (event.key() == GLFW_KEY_ESCAPE && spmap$drawing) {
            spmap$drawing = false;
            spmap$hasStart = false;
            spmap$previewArea = null;
            spmap$resetChunkTracking();
            spmap$textField.setVisible(false);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("RETURN"), remap = true)
    private void spmap$render(DrawContext graphics, int scaledMouseX, int scaledMouseY,
                              float partialTicks, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        MapDrawing.ensureLoaded(client);
        if (!spmap$isOnSpmapWidget(scaledMouseX, scaledMouseY)) {
            spmap$syncMouseButtons(client);
        }
        if (spmap$gridVisible) {
            spmap$renderGrid(graphics);
        }
        spmap$renderDrawings(graphics);

        if (spmap$hasStart && spmap$mode == MapDrawing.Type.LINE) {
            spmap$line(graphics, spmap$screenX(spmap$startX), spmap$screenZ(spmap$startZ),
                spmap$screenX(mouseBlockPosX), spmap$screenZ(mouseBlockPosZ), 0x663B82F6);
        }
        if (spmap$hasStart && spmap$mode == MapDrawing.Type.AREA) {
            int minChunkX = Math.min(Math.floorDiv(spmap$startX, 16), Math.floorDiv(mouseBlockPosX, 16));
            int minChunkZ = Math.min(Math.floorDiv(spmap$startZ, 16), Math.floorDiv(mouseBlockPosZ, 16));
            int maxChunkX = Math.max(Math.floorDiv(spmap$startX, 16), Math.floorDiv(mouseBlockPosX, 16));
            int maxChunkZ = Math.max(Math.floorDiv(spmap$startZ, 16), Math.floorDiv(mouseBlockPosZ, 16));
            spmap$rect(graphics, spmap$screenX(minChunkX * 16), spmap$screenZ(minChunkZ * 16),
                spmap$screenX((maxChunkX + 1) * 16), spmap$screenZ((maxChunkZ + 1) * 16), 0x663B82F6);
        }

        if (spmap$notice != null && System.currentTimeMillis() < spmap$noticeUntil) {
            Screen screen = (Screen) (Object) this;
            graphics.drawCenteredTextWithShadow(client.textRenderer, spmap$notice, screen.width / 2, 30, 0xFFFFFFFF);
        }
    }

    @Unique
    private void spmap$addCurrentChunk() {
        int chunkX = Math.floorDiv(mouseBlockPosX, 16);
        int chunkZ = Math.floorDiv(mouseBlockPosZ, 16);
        if (chunkX == spmap$lastChunkX && chunkZ == spmap$lastChunkZ) {
            return;
        }
        spmap$lastChunkX = chunkX;
        spmap$lastChunkZ = chunkZ;
        MapDrawing.addChunk(MinecraftClient.getInstance(), new MapDrawing.Chunk(chunkX, chunkZ, spmap$color));
    }

    @Unique
    private void spmap$eraseCurrentChunk() {
        int chunkX = Math.floorDiv(mouseBlockPosX, 16);
        int chunkZ = Math.floorDiv(mouseBlockPosZ, 16);
        if (chunkX == spmap$lastErasedChunkX && chunkZ == spmap$lastErasedChunkZ) {
            return;
        }
        spmap$lastErasedChunkX = chunkX;
        spmap$lastErasedChunkZ = chunkZ;
        MapDrawing.eraseAt(MinecraftClient.getInstance(), mouseBlockPosX, mouseBlockPosZ);
    }

    @Unique
    private void spmap$syncMouseButtons(MinecraftClient client) {
        if (!spmap$drawing || client.getWindow() == null) {
            return;
        }
        long handle = client.getWindow().getHandle();
        spmap$leftMouseDown = glfwGetMouseButton(handle, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        spmap$rightMouseDown = glfwGetMouseButton(handle, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;
        if (spmap$leftMouseDown && spmap$mode == MapDrawing.Type.CHUNK) {
            spmap$addCurrentChunk();
        }
        if (spmap$rightMouseDown && !spmap$hasStart) {
            spmap$eraseCurrentChunk();
        }
    }

    @Unique
    private void spmap$resetChunkTracking() {
        spmap$lastChunkX = Integer.MIN_VALUE;
        spmap$lastChunkZ = Integer.MIN_VALUE;
        spmap$lastErasedChunkX = Integer.MIN_VALUE;
        spmap$lastErasedChunkZ = Integer.MIN_VALUE;
    }

    @Unique
    private boolean spmap$isOnSpmapWidget(double mouseX, double mouseY) {
        return spmap$isMouseOver(spmap$drawButton, mouseX, mouseY)
            || spmap$isMouseOver(spmap$menuButton, mouseX, mouseY)
            || spmap$isMouseOver(spmap$gridButton, mouseX, mouseY)
            || spmap$isMouseOver(spmap$chunkButton, mouseX, mouseY)
            || spmap$isMouseOver(spmap$areaButton, mouseX, mouseY)
            || spmap$isMouseOver(spmap$textButton, mouseX, mouseY)
            || spmap$isMouseOver(spmap$colorButton, mouseX, mouseY)
            || spmap$isMouseOver(spmap$coordsButton, mouseX, mouseY)
            || spmap$isMouseOver(spmap$exportButton, mouseX, mouseY)
            || spmap$isMouseOver(spmap$removeCoordinatesButton, mouseX, mouseY)
            || spmap$isMouseOver(spmap$undoButton, mouseX, mouseY)
            || (spmap$textField != null && spmap$textField.isVisible() && spmap$textField.isMouseOver(mouseX, mouseY));
    }

    @Unique
    private boolean spmap$isMouseOver(ButtonWidget button, double mouseX, double mouseY) {
        return button != null && button.visible && button.isMouseOver(mouseX, mouseY);
    }

    @Unique
    private void spmap$renderGrid(DrawContext graphics) {
        double mapScale = spmap$mapScale();
        double viewportWidth = spmap$viewportWidth();
        double viewportHeight = spmap$viewportHeight();
        int minX = (int) Math.floor(cameraX - viewportWidth / (2.0 * mapScale)) - 16;
        int minZ = (int) Math.floor(cameraZ - viewportHeight / (2.0 * mapScale)) - 16;
        int maxX = (int) Math.ceil(cameraX + viewportWidth / (2.0 * mapScale)) + 16;
        int maxZ = (int) Math.ceil(cameraZ + viewportHeight / (2.0 * mapScale)) + 16;
        int lineCount = Math.max(1, (maxX - minX) / 16 + (maxZ - minZ) / 16);
        int stride = Math.max(1, (lineCount + 250) / 250);
        int firstX = Math.floorDiv(minX, 16) * 16;
        int firstZ = Math.floorDiv(minZ, 16) * 16;
        for (int x = firstX; x <= maxX; x += 16 * stride) {
            int screenX = spmap$screenX(x);
            graphics.fill(screenX, 0, screenX + 1, (int) Math.ceil(viewportHeight), 0x77333333);
        }
        for (int z = firstZ; z <= maxZ; z += 16 * stride) {
            int screenZ = spmap$screenZ(z);
            graphics.fill(0, screenZ, (int) Math.ceil(viewportWidth), screenZ + 1, 0x77333333);
        }
    }

    @Unique
    private void spmap$renderDrawings(DrawContext graphics) {
        for (MapDrawing.Line line : MapDrawing.lines()) {
            spmap$line(graphics, spmap$screenX(line.x1()), spmap$screenZ(line.z1()),
                spmap$screenX(line.x2()), spmap$screenZ(line.z2()), line.color());
        }
        for (MapDrawing.Chunk chunk : MapDrawing.chunks()) {
            spmap$rect(graphics, spmap$screenX(chunk.x() * 16), spmap$screenZ(chunk.z() * 16),
                spmap$screenX((chunk.x() + 1) * 16), spmap$screenZ((chunk.z() + 1) * 16), chunk.color());
        }
        spmap$renderChunkLabels(graphics);
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        for (MapDrawing.Area area : MapDrawing.areas()) {
            int minX = spmap$screenX(area.minChunkX() * 16);
            int minZ = spmap$screenZ(area.minChunkZ() * 16);
            int maxX = spmap$screenX((area.maxChunkX() + 1) * 16);
            int maxZ = spmap$screenZ((area.maxChunkZ() + 1) * 16);
            spmap$rect(graphics, minX, minZ, maxX, maxZ, area.color());
            if (area.equals(MapDrawing.lastArea())) {
                graphics.drawTextWithShadow(renderer, "СЗ " + area.minChunkX() + "," + area.minChunkZ(),
                    minX + 2, minZ + 2, 0xFFFFFFFF);
                graphics.drawTextWithShadow(renderer, "ЮВ " + area.maxChunkX() + "," + area.maxChunkZ(),
                    maxX - 80, maxZ - 12, 0xFFFFFFFF);
            }
        }
        for (MapDrawing.Text text : MapDrawing.texts()) {
            graphics.drawTextWithShadow(renderer, text.value(),
                spmap$screenX(text.x()), spmap$screenZ(text.z()), text.color());
        }
    }

    @Unique
    private void spmap$renderChunkLabels(DrawContext graphics) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer renderer = client.textRenderer;
        Screen screen = (Screen) (Object) this;
        java.util.List<int[]> placed = new java.util.ArrayList<>();
        int labelHeight = renderer.fontHeight;

        for (MapDrawing.CoordinateLabel label : MapDrawing.coordinateLabels()) {
            String value = "X: " + label.x() + ", Z: " + label.z();
            int labelWidth = renderer.getWidth(value);
            int centerX = spmap$screenX(label.x());
            int centerZ = spmap$screenZ(label.z());
            int baseX = centerX - labelWidth / 2;
            int baseY = centerZ - labelHeight / 2;
            int[] chosen = null;

            for (int radius = 0; radius <= 64 && chosen == null; radius += 16) {
                for (int[] offset : spmap$labelOffsets(radius)) {
                    int candidateX = baseX + offset[0];
                    int candidateY = baseY + offset[1];
                    int[] rect = new int[] {
                        candidateX - 2, candidateY - 2,
                        candidateX + labelWidth + 2, candidateY + labelHeight + 2
                    };
                    if (rect[2] < 0 || rect[0] > screen.width
                        || rect[3] < 0 || rect[1] > screen.height) {
                        continue;
                    }
                    boolean overlaps = false;
                    for (int[] other : placed) {
                        if (rect[0] < other[2] && rect[2] > other[0]
                            && rect[1] < other[3] && rect[3] > other[1]) {
                            overlaps = true;
                            break;
                        }
                    }
                    if (!overlaps) {
                        chosen = rect;
                        break;
                    }
                }
            }

            if (chosen == null) {
                chosen = new int[] {
                    baseX - 2, baseY - 2,
                    baseX + labelWidth + 2, baseY + labelHeight + 2
                };
            }
            placed.add(chosen);
            graphics.drawTextWithShadow(renderer, value,
                chosen[0] + 2, chosen[1] + 2, label.color());
        }
    }

    @Unique
    private java.util.List<int[]> spmap$labelOffsets(int radius) {
        if (radius == 0) {
            return java.util.List.of(new int[] {0, 0});
        }
        return java.util.List.of(
            new int[] {0, -radius},
            new int[] {radius, 0},
            new int[] {0, radius},
            new int[] {-radius, 0},
            new int[] {radius, -radius},
            new int[] {radius, radius},
            new int[] {-radius, radius},
            new int[] {-radius, -radius}
        );
    }

    @Unique
    private void spmap$line(DrawContext graphics, int x1, int z1, int x2, int z2, int color) {
        int dx = Math.abs(x2 - x1);
        int dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1;
        int sz = z1 < z2 ? 1 : -1;
        int error = dx - dz;
        while (true) {
            graphics.fill(x1, z1, x1 + 2, z1 + 2, color);
            if (x1 == x2 && z1 == z2) return;
            int twice = error * 2;
            if (twice > -dz) {
                error -= dz;
                x1 += sx;
            }
            if (twice < dx) {
                error += dx;
                z1 += sz;
            }
        }
    }

    @Unique
    private void spmap$rect(DrawContext graphics, int x1, int z1, int x2, int z2, int color) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        graphics.fill(minX, minZ, maxX, minZ + 2, color);
        graphics.fill(minX, maxZ - 2, maxX, maxZ, color);
        graphics.fill(minX, minZ, minX + 2, maxZ, color);
        graphics.fill(maxX - 2, minZ, maxX, maxZ, color);
    }

    @Unique
    private double spmap$mapScale() {
        double currentScale = scale > 0.00001 ? scale : destScale;
        return Math.max(0.00001, currentScale / spmap$guiScale());
    }

    @Unique
    private int spmap$screenX(int blockX) {
        return (int) Math.floor(spmap$viewportWidth() / 2.0
            + (blockX - cameraX) * spmap$mapScale());
    }

    @Unique
    private int spmap$screenZ(int blockZ) {
        return (int) Math.floor(spmap$viewportHeight() / 2.0
            + (blockZ - cameraZ) * spmap$mapScale());
    }

    @Unique
    private double spmap$guiScale() {
        return screenScale > 0.00001 ? screenScale : 1.0;
    }

    @Unique
    private double spmap$viewportWidth() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getWindow().getFramebufferWidth() / spmap$guiScale();
    }

    @Unique
    private double spmap$viewportHeight() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getWindow().getFramebufferHeight() / spmap$guiScale();
    }
}
