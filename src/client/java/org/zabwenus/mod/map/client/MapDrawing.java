package org.zabwenus.mod.map.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public final class MapDrawing {
    public enum Type {
        LINE,
        CHUNK,
        AREA,
        TEXT,
        COORDINATE,
        REMOVE_COORDINATE
    }

    public record Line(int x1, int z1, int x2, int z2, int color) {
    }

    public record Chunk(int x, int z, int color) {
    }

    public record CoordinateLabel(int x, int z, int color) {
    }

    public record Area(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int color) {
        public Area {
            if (minChunkX > maxChunkX) {
                int value = minChunkX;
                minChunkX = maxChunkX;
                maxChunkX = value;
            }
            if (minChunkZ > maxChunkZ) {
                int value = minChunkZ;
                minChunkZ = maxChunkZ;
                maxChunkZ = value;
            }
        }
    }

    public record Text(int x, int z, String value, int color) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Line> LINES = new ArrayList<>();
    private static final List<Chunk> CHUNKS = new ArrayList<>();
    private static final List<CoordinateLabel> COORDINATE_LABELS = new ArrayList<>();
    private static final List<Area> AREAS = new ArrayList<>();
    private static final List<Text> TEXTS = new ArrayList<>();
    private static final Deque<UndoAction> UNDO = new ArrayDeque<>();
    private static String loadedKey;
    private static Area lastArea;

    private MapDrawing() {
    }

    public static List<Line> lines() {
        return Collections.unmodifiableList(LINES);
    }

    public static List<Chunk> chunks() {
        return Collections.unmodifiableList(CHUNKS);
    }

    public static List<CoordinateLabel> coordinateLabels() {
        return Collections.unmodifiableList(COORDINATE_LABELS);
    }

    public static List<Area> areas() {
        return Collections.unmodifiableList(AREAS);
    }

    public static List<Text> texts() {
        return Collections.unmodifiableList(TEXTS);
    }

    public static Area lastArea() {
        return lastArea;
    }

    public static void ensureLoaded(MinecraftClient minecraft) {
        String key = worldKey(minecraft);
        if (key.equals(loadedKey)) {
            return;
        }

        loadedKey = key;
        LINES.clear();
        CHUNKS.clear();
        COORDINATE_LABELS.clear();
        AREAS.clear();
        TEXTS.clear();
        UNDO.clear();
        lastArea = null;

        Path path = filePath(minecraft, key);
        if (!Files.isRegularFile(path)) {
            return;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            readLines(root.getAsJsonArray("lines"));
            readChunks(root.getAsJsonArray("chunks"));
            readCoordinates(root.getAsJsonArray("coordinates"));
            readLegacyChunkLabels(root.getAsJsonArray("chunkLabels"));
            readAreas(root.getAsJsonArray("areas"));
            readTexts(root.getAsJsonArray("texts"));
            if (!AREAS.isEmpty()) {
                lastArea = AREAS.getLast();
            }
        } catch (Exception ignored) {
            // A malformed local drawing file must not prevent the map from opening.
        }
    }

    public static void addLine(MinecraftClient minecraft, Line line) {
        ensureLoaded(minecraft);
        LINES.add(line);
        UNDO.push(() -> LINES.remove(line));
        save(minecraft);
    }

    public static void addChunk(MinecraftClient minecraft, Chunk chunk) {
        ensureLoaded(minecraft);
        for (int i = 0; i < CHUNKS.size(); i++) {
            Chunk old = CHUNKS.get(i);
            if (old.x == chunk.x && old.z == chunk.z) {
                int index = i;
                CHUNKS.set(index, chunk);
                UNDO.push(() -> CHUNKS.set(index, old));
                save(minecraft);
                return;
            }
        }
        CHUNKS.add(chunk);
        UNDO.push(() -> CHUNKS.remove(chunk));
        save(minecraft);
    }

    public static void addCoordinateLabel(MinecraftClient minecraft, int x, int z) {
        ensureLoaded(minecraft);
        for (CoordinateLabel label : COORDINATE_LABELS) {
            if (label.x == x && label.z == z) {
                return;
            }
        }
        CoordinateLabel label = new CoordinateLabel(x, z, 0xFFFFFFFF);
        COORDINATE_LABELS.add(label);
        UNDO.push(() -> COORDINATE_LABELS.remove(label));
        save(minecraft);
    }

    public static boolean removeCoordinateAt(MinecraftClient minecraft, int x, int z) {
        ensureLoaded(minecraft);
        CoordinateLabel nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (CoordinateLabel label : COORDINATE_LABELS) {
            double distance = Math.hypot(label.x - x, label.z - z);
            if (distance <= 12.0 && distance < nearestDistance) {
                nearest = label;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            return false;
        }
        int index = COORDINATE_LABELS.indexOf(nearest);
        COORDINATE_LABELS.remove(index);
        CoordinateLabel removed = nearest;
        UNDO.push(() -> COORDINATE_LABELS.add(Math.min(index, COORDINATE_LABELS.size()), removed));
        save(minecraft);
        return true;
    }

    public static String exportCoordinates(MinecraftClient minecraft) {
        ensureLoaded(minecraft);
        Path path = minecraft.runDirectory.toPath().resolve("config").resolve("spmap")
            .resolve(worldKey(minecraft) + "-coordinates.txt");
        StringBuilder content = new StringBuilder();
        for (CoordinateLabel label : COORDINATE_LABELS) {
            content.append("X: ").append(label.x)
                .append(", Z: ").append(label.z)
                .append(System.lineSeparator());
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content.toString(), StandardCharsets.UTF_8);
            return path.toString();
        } catch (IOException ignored) {
            return null;
        }
    }

    public static void addArea(MinecraftClient minecraft, Area area) {
        ensureLoaded(minecraft);
        AREAS.add(area);
        lastArea = area;
        UNDO.push(() -> AREAS.remove(area));
        save(minecraft);
    }

    public static void addText(MinecraftClient minecraft, Text text) {
        ensureLoaded(minecraft);
        for (int i = 0; i < TEXTS.size(); i++) {
            Text old = TEXTS.get(i);
            if (old.x == text.x && old.z == text.z) {
                int index = i;
                TEXTS.set(index, text);
                UNDO.push(() -> TEXTS.set(index, old));
                save(minecraft);
                return;
            }
        }
        TEXTS.add(text);
        UNDO.push(() -> TEXTS.remove(text));
        save(minecraft);
    }

    public static void addCornerLabels(MinecraftClient minecraft, Area area) {
        ensureLoaded(minecraft);
        if (area == null) {
            return;
        }

        List<Text> added = new ArrayList<>();
        List<IndexedText> replaced = new ArrayList<>();
        putCornerLabel(new Text(area.minChunkX * 16 + 8, area.minChunkZ * 16 + 8,
            "СЗ [" + area.minChunkX + ", " + area.minChunkZ + "]", 0xFFFFFFFF), added, replaced);
        putCornerLabel(new Text(area.maxChunkX * 16 + 8, area.minChunkZ * 16 + 8,
            "СВ [" + area.maxChunkX + ", " + area.minChunkZ + "]", 0xFFFFFFFF), added, replaced);
        putCornerLabel(new Text(area.minChunkX * 16 + 8, area.maxChunkZ * 16 + 8,
            "ЮЗ [" + area.minChunkX + ", " + area.maxChunkZ + "]", 0xFFFFFFFF), added, replaced);
        putCornerLabel(new Text(area.maxChunkX * 16 + 8, area.maxChunkZ * 16 + 8,
            "ЮВ [" + area.maxChunkX + ", " + area.maxChunkZ + "]", 0xFFFFFFFF), added, replaced);

        if (added.isEmpty() && replaced.isEmpty()) {
            return;
        }
        UNDO.push(() -> {
            added.forEach(TEXTS::remove);
            for (IndexedText text : replaced) {
                if (text.index < TEXTS.size()) {
                    TEXTS.set(text.index, text.value);
                } else {
                    TEXTS.add(text.value);
                }
            }
        });
        save(minecraft);
    }

    public static void eraseAt(MinecraftClient minecraft, int x, int z) {
        ensureLoaded(minecraft);
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
        List<Line> lines = new ArrayList<>();
        for (Line line : LINES) {
            if (distanceToSegment(x, z, line.x1, line.z1, line.x2, line.z2) <= 12.0) {
                lines.add(line);
            }
        }
        List<Chunk> chunks = CHUNKS.stream()
            .filter(chunk -> chunk.x == chunkX && chunk.z == chunkZ)
            .toList();
        List<CoordinateLabel> coordinateLabels = COORDINATE_LABELS.stream()
            .filter(label -> Math.hypot(label.x - x, label.z - z) <= 12.0)
            .toList();
        List<Area> areas = AREAS.stream()
            .filter(area -> chunkX >= area.minChunkX && chunkX <= area.maxChunkX
                && chunkZ >= area.minChunkZ && chunkZ <= area.maxChunkZ)
            .toList();
        List<Text> texts = TEXTS.stream()
            .filter(text -> Math.abs(text.x - x) <= 24 && Math.abs(text.z - z) <= 24)
            .toList();

        if (lines.isEmpty() && chunks.isEmpty() && coordinateLabels.isEmpty()
            && areas.isEmpty() && texts.isEmpty()) {
            return;
        }

        lines.forEach(LINES::remove);
        chunks.forEach(CHUNKS::remove);
        coordinateLabels.forEach(COORDINATE_LABELS::remove);
        areas.forEach(AREAS::remove);
        texts.forEach(TEXTS::remove);
        UNDO.push(() -> {
            LINES.addAll(lines);
            CHUNKS.addAll(chunks);
            COORDINATE_LABELS.addAll(coordinateLabels);
            AREAS.addAll(areas);
            TEXTS.addAll(texts);
        });
        lastArea = AREAS.isEmpty() ? null : AREAS.getLast();
        save(minecraft);
    }

    public static void undo(MinecraftClient minecraft) {
        ensureLoaded(minecraft);
        if (UNDO.isEmpty()) {
            return;
        }
        UNDO.pop().undo();
        lastArea = AREAS.isEmpty() ? null : AREAS.getLast();
        save(minecraft);
    }

    public static String cornerCoordinates(Area area) {
        if (area == null) {
            return "Нет выбранной территории";
        }
        return "СЗ [" + area.minChunkX + ", " + area.minChunkZ + "] | "
            + "СВ [" + area.maxChunkX + ", " + area.minChunkZ + "] | "
            + "ЮЗ [" + area.minChunkX + ", " + area.maxChunkZ + "] | "
            + "ЮВ [" + area.maxChunkX + ", " + area.maxChunkZ + "]";
    }

    public static String cornerCoordinatesBlocks(Area area) {
        if (area == null) {
            return "Нет выбранной территории";
        }
        int minX = area.minChunkX * 16;
        int minZ = area.minChunkZ * 16;
        int maxX = area.maxChunkX * 16 + 15;
        int maxZ = area.maxChunkZ * 16 + 15;
        return "СЗ [" + minX + ", " + minZ + "] | СВ [" + maxX + ", " + minZ + "] | "
            + "ЮЗ [" + minX + ", " + maxZ + "] | ЮВ [" + maxX + ", " + maxZ + "]";
    }

    private static void readLines(JsonArray array) {
        if (array == null) return;
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            LINES.add(new Line(object.get("x1").getAsInt(), object.get("z1").getAsInt(),
                object.get("x2").getAsInt(), object.get("z2").getAsInt(), object.get("color").getAsInt()));
        }
    }

    private static void readChunks(JsonArray array) {
        if (array == null) return;
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            CHUNKS.add(new Chunk(object.get("x").getAsInt(), object.get("z").getAsInt(), object.get("color").getAsInt()));
        }
    }

    private static void readCoordinates(JsonArray array) {
        if (array == null) return;
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            COORDINATE_LABELS.add(new CoordinateLabel(
                object.get("x").getAsInt(),
                object.get("z").getAsInt(),
                object.get("color").getAsInt()
            ));
        }
    }

    private static void readLegacyChunkLabels(JsonArray array) {
        if (array == null) return;
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            COORDINATE_LABELS.add(new CoordinateLabel(
                object.get("chunkX").getAsInt() * 16 + 8,
                object.get("chunkZ").getAsInt() * 16 + 8,
                object.get("color").getAsInt()
            ));
        }
    }

    private static void readAreas(JsonArray array) {
        if (array == null) return;
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            AREAS.add(new Area(object.get("minChunkX").getAsInt(), object.get("minChunkZ").getAsInt(),
                object.get("maxChunkX").getAsInt(), object.get("maxChunkZ").getAsInt(), object.get("color").getAsInt()));
        }
    }

    private static void readTexts(JsonArray array) {
        if (array == null) return;
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            TEXTS.add(new Text(object.get("x").getAsInt(), object.get("z").getAsInt(),
                object.get("value").getAsString(), object.get("color").getAsInt()));
        }
    }

    private static void save(MinecraftClient minecraft) {
        Path path = filePath(minecraft, loadedKey == null ? worldKey(minecraft) : loadedKey);
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.add("lines", GSON.toJsonTree(LINES));
            root.add("chunks", GSON.toJsonTree(CHUNKS));
            root.add("coordinates", GSON.toJsonTree(COORDINATE_LABELS));
            root.add("areas", GSON.toJsonTree(AREAS));
            root.add("texts", GSON.toJsonTree(TEXTS));
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // The current session remains usable if disk persistence is unavailable.
        }
    }

    private static Path filePath(MinecraftClient minecraft, String key) {
        return minecraft.runDirectory.toPath().resolve("config").resolve("spmap").resolve(key + ".json");
    }

    private static String worldKey(MinecraftClient minecraft) {
        String value;
        if (minecraft.getCurrentServerEntry() != null) {
            value = "server-" + minecraft.getCurrentServerEntry().address;
        } else {
            value = "singleplayer-" + minecraft.getSession().getUsername();
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static double distanceToSegment(double px, double pz, double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        if (dx == 0 && dz == 0) {
            return Math.hypot(px - x1, pz - z1);
        }
        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (pz - z1) * dz) / (dx * dx + dz * dz)));
        return Math.hypot(px - (x1 + t * dx), pz - (z1 + t * dz));
    }

    private static void putCornerLabel(Text text, List<Text> added, List<IndexedText> replaced) {
        for (int i = 0; i < TEXTS.size(); i++) {
            Text old = TEXTS.get(i);
            if (old.x == text.x && old.z == text.z) {
                if (!old.value.equals(text.value) || old.color != text.color) {
                    TEXTS.set(i, text);
                    replaced.add(new IndexedText(i, old));
                }
                return;
            }
        }
        TEXTS.add(text);
        added.add(text);
    }

    private record IndexedText(int index, Text value) {
    }

    @FunctionalInterface
    private interface UndoAction {
        void undo();
    }
}
