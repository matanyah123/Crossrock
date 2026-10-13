package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Conservative blocks.json adapter for Java's single-state, untinted cube models. */
final class BedrockCubeAssetTranslator {
    private static final List<String> FACES = List.of("down", "up", "north", "south", "west", "east");
    static void translate(Map<String, byte[]> content, Map<String, byte[]> animations, GeneratedResourceSink sink) throws IOException {
        if (!content.containsKey("blocks.json") || !content.containsKey("textures/terrain_texture.json")) return;
        JsonObject blocks = parse(content.get("blocks.json"));
        JsonObject atlas = parse(content.get("textures/terrain_texture.json")).getAsJsonObject("texture_data");
        if (atlas == null) return;
        for (var entry : blocks.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id == null || !id.getNamespace().equals("minecraft")) continue;
            JsonObject state = vanilla("blockstates/" + id.getPath() + ".json");
            if (state == null || !state.has("variants")) continue;
            JsonObject variants = state.getAsJsonObject("variants");
            if (variants.size() != 1 || !variants.has("") || !variants.get("").isJsonObject()) continue;
            JsonObject variant = variants.getAsJsonObject("");
            if (variant.size() != 1 || !variant.has("model")) continue;
            Identifier modelId = Identifier.tryParse(variant.get("model").getAsString());
            if (modelId == null || !modelId.getNamespace().equals("minecraft")) continue;
            JsonObject original = vanilla("models/" + modelId.getPath() + ".json");
            if (original == null || original.has("elements") || !original.has("parent")) continue;
            if (!Set.of("minecraft:block/cube_all", "minecraft:block/cube").contains(original.get("parent").getAsString())) continue;
            JsonObject definition = entry.getValue().getAsJsonObject();
            emit(definition.get("textures"), atlas, content, animations, sink, "models/" + modelId.getPath() + ".json");
            if (definition.has("carried_textures")) {
                String carried = "block/crossrock_carried/" + id.getPath();
                if (emit(definition.get("carried_textures"), atlas, content, animations, sink, "models/" + carried + ".json")) {
                    JsonObject itemModel = new JsonObject();
                    itemModel.addProperty("type", "minecraft:model"); itemModel.addProperty("model", "minecraft:" + carried);
                    JsonObject item = new JsonObject(); item.add("model", itemModel);
                    put(sink, "items/" + id.getPath() + ".json", item);
                }
            }
        }
    }
    private static boolean emit(JsonElement assignment, JsonObject atlas, Map<String, byte[]> content,
                                Map<String, byte[]> animations, GeneratedResourceSink sink, String output) throws IOException {
        Map<String, VanillaTextureTranslator.ResolvedTexture> resolved = new LinkedHashMap<>();
        for (String face : FACES) {
            JsonElement alias = assignment;
            if (assignment != null && assignment.isJsonObject()) {
                JsonObject faces = assignment.getAsJsonObject(); alias = faces.get(face);
                if (alias == null && !face.equals("up") && !face.equals("down")) alias = faces.get("side");
                if (alias == null) alias = faces.get("*");
            }
            if (alias == null || !alias.isJsonPrimitive() || !alias.getAsJsonPrimitive().isString()) return false;
            JsonElement tile = atlas.get(alias.getAsString());
            if (tile == null || !tile.isJsonObject()) return false;
            JsonElement path = tile.getAsJsonObject().get("textures");
            // Indexed/weighted terrain variants need a runtime selector; never choose a random substitute.
            if (path == null || !path.isJsonPrimitive() || !path.getAsJsonPrimitive().isString()) return false;
            var texture = VanillaTextureTranslator.resolveTexture(path, content);
            if (texture == null || texture.data().length == 0) return false;
            resolved.put(face, texture);
        }
        JsonObject slots = new JsonObject();
        for (var face : resolved.entrySet()) {
            String texture = "block/crossrock_assets/" + face.getValue().path().replaceAll("\\.(png|tga)$", "");
            // Keep source identity rather than mapping an arbitrary atlas alias to a vanilla filename.
            if (Identifier.tryParse("minecraft:" + texture) == null || texture.contains("..")) return false;
            putBytes(sink, "textures/" + texture + ".png", face.getValue().data());
            byte[] animation = animations.get(face.getValue().path());
            if (animation != null) putBytes(sink, "textures/" + texture + ".png.mcmeta", animation);
            slots.addProperty(face.getKey(), "minecraft:" + texture);
        }
        slots.add("particle", slots.get("north"));
        JsonObject model = new JsonObject(); model.addProperty("parent", "minecraft:block/cube"); model.add("textures", slots);
        put(sink, output, model); return true;
    }
    private static JsonObject parse(byte[] data) { return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static JsonObject vanilla(String path) throws IOException {
        try (var in = BedrockCubeAssetTranslator.class.getResourceAsStream("/assets/minecraft/" + path)) {
            return in == null ? null : parse(in.readAllBytes());
        }
    }
    private static void put(GeneratedResourceSink sink, String path, JsonObject json) { putBytes(sink, path, json.toString().getBytes(StandardCharsets.UTF_8)); }
    private static void putBytes(GeneratedResourceSink sink, String path, byte[] bytes) { sink.put(Identifier.withDefaultNamespace(path), bytes); }
}
