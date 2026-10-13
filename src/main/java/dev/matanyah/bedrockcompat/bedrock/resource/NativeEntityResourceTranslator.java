package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Publishes native Bedrock definitions with resolver annotations, never Java model JSON. */
public final class NativeEntityResourceTranslator implements BedrockResourceTranslator {
    private final BedrockPackContentReader reader;
    public NativeEntityResourceTranslator(BedrockPackContentReader reader) { this.reader = reader; }

    @Override public void translate(BedrockPack pack, BedrockPackConfiguration configuration,
                                    GeneratedResourceSink sink) throws IOException {
        // Buffer by original path first: the selected subpack replaces the entire source document.
        Map<String, byte[]> documents = new LinkedHashMap<>();
        Map<String, byte[]> eggAssets = new LinkedHashMap<>();
        List<String> failures=new ArrayList<>();
        reader.visitAll(pack, configuration.selectedSubpack(), (path, data) -> {
            String p = path.replace('\\', '/');
            if(p.equals("textures/item_texture.json")||p.startsWith("textures/")&&(p.endsWith(".png")||p.endsWith(".tga")))eggAssets.put(p,data);
            if (p.endsWith(".json") && (p.startsWith("entity/") || p.startsWith("attachables/") || p.startsWith("models/entity/")
                    || p.startsWith("animations/") || p.startsWith("animation_controllers/")
                    || p.startsWith("render_controllers/") || p.startsWith("particles/") || p.startsWith("biomes/") || p.equals("models/mobs.json"))) documents.put(p, data);
            if(p.startsWith("materials/")&&p.endsWith(".material"))documents.put(p,data);
            if (p.startsWith("textures/") && p.endsWith(".png") && Identifier.isValidPath(p))
                sink.put(Identifier.fromNamespaceAndPath("bedrock_native", p), data);
            if(p.startsWith("textures/")&&p.endsWith(".tga")&&Identifier.isValidPath(p)) {
                byte[] image=VanillaTextureTranslator.decodeTga(data);
                if(image==null)failures.add("Unsupported native TGA texture: "+p);
                else sink.put(Identifier.fromNamespaceAndPath("bedrock_native",p.substring(0,p.length()-4)+".png"),image);
            }
        });
        for (var entry : documents.entrySet()) {
            try {
                JsonObject root = JsonParser.parseString(new String(entry.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
                if(root.has("minecraft:client_entity"))try {
                    BedrockSpawnEgg.translate(root.getAsJsonObject("minecraft:client_entity").getAsJsonObject("description"),eggAssets,sink);
                } catch(IOException|RuntimeException failure) {failures.add("spawn_egg in "+entry.getKey()+": "+failure.getMessage());}
                for (var definition : definitions(root).entrySet()) {
                    JsonObject envelope = new JsonObject();
                    envelope.addProperty("source", entry.getKey());
                    envelope.addProperty("id", definition.getKey());
                    envelope.add("definition", definition.getValue());
                    envelope.add("pack_settings", new Gson().toJsonTree(configuration.mergedWithDefaults(pack).settingValues()));
                    // Encode names bijectively; punctuation replacement could merge unrelated definitions.
                    String key = HexFormat.of().formatHex(definition.getKey().getBytes(StandardCharsets.UTF_8));
                    sink.put(Identifier.fromNamespaceAndPath("bedrock_native", "entity_definitions/" + key + ".json"),
                        envelope.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (RuntimeException exception) {
                failures.add("Invalid native entity document " + entry.getKey()+": "+exception.getMessage());
            }
        }
        if(!failures.isEmpty())throw new IOException(String.join("; ",failures));
    }

    public static Map<String, JsonObject> definitions(JsonObject root) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        if(root.has("minecraft:biome")) {
            var biome=root.getAsJsonObject("minecraft:biome");
            String id=biome.getAsJsonObject("description").get("identifier").getAsString();
            out.put("biome."+(id.contains(":")?id:"minecraft:"+id),biome);
        }
        if(root.has("particle_effect")) {
            var effect=root.getAsJsonObject("particle_effect");
            out.put("particle."+effect.getAsJsonObject("description").get("identifier").getAsString(),effect);
        }
        if(root.has("materials")&&root.get("materials").isJsonObject())
            root.getAsJsonObject("materials").entrySet().forEach(entry-> {
                if(!entry.getValue().isJsonObject())return;
                String[] parts=entry.getKey().split(":",2);
                JsonObject material=entry.getValue().getAsJsonObject().deepCopy();
                if(parts.length==2)material.addProperty("bedrock_parent",parts[1]);
                out.put("material."+parts[0],material);
            });
        if (root.has("minecraft:client_entity")) {
            JsonObject value = root.getAsJsonObject("minecraft:client_entity");
            out.put(value.getAsJsonObject("description").get("identifier").getAsString(), value);
        }
        if (root.has("minecraft:attachable")) {
            JsonObject value = root.getAsJsonObject("minecraft:attachable");
            // Items and entities may share an identifier. Their definition namespaces must not.
            out.put("attachable." + value.getAsJsonObject("description").get("identifier").getAsString(), value);
        }
        for (String group : List.of("animations", "animation_controllers", "render_controllers")) {
            if (root.has(group)) root.getAsJsonObject(group).entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsJsonObject()));
        }
        if (root.has("minecraft:geometry")) for (JsonElement element : root.getAsJsonArray("minecraft:geometry")) {
            JsonObject value = element.getAsJsonObject();
            out.put(value.getAsJsonObject("description").get("identifier").getAsString(), value);
        }
        // Legacy geometry identifiers are the object keys; preserve bones and texture dimensions.
        root.entrySet().stream().filter(e -> e.getKey().startsWith("geometry.") && e.getValue().isJsonObject())
            .forEach(e -> {
                String[] names=e.getKey().split(":",2);
                JsonObject value=e.getValue().getAsJsonObject().deepCopy();
                if(names.length==2)value.addProperty("bedrock_parent",names[1]);
                out.put(names[0],value);
            });
        return out;
    }
}
