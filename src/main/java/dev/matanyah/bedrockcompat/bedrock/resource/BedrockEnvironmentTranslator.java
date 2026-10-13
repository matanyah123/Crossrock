package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.entity.BedrockBiomes;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Identity-keyed resources give environment definitions the same pack/subpack priority as assets. */
public final class BedrockEnvironmentTranslator implements BedrockResourceTranslator {
    private final BedrockPackContentReader reader;
    public BedrockEnvironmentTranslator(BedrockPackContentReader reader) { this.reader=reader; }
    @Override public void translate(BedrockPack pack, BedrockPackConfiguration config, GeneratedResourceSink sink) throws IOException {
        Map<String,byte[]> documents=new LinkedHashMap<>();
        reader.visitAll(pack,config.selectedSubpack(),(path,data)->{
            if(path.equals("biomes_client.json") || path.endsWith(".json") &&
                (path.startsWith("biomes/")||path.startsWith("biomes_client/")||path.startsWith("fogs/")
                ||path.startsWith("atmospherics/")||path.startsWith("lighting/"))) documents.put(path,data);
        });
        List<String> failures=new ArrayList<>();
        // Modern client-biome definitions override legacy entries within a pack, irrespective of traversal order.
        var ordered=new ArrayList<>(documents.entrySet());
        ordered.sort(Comparator.comparingInt(e->e.getKey().equals("biomes_client.json")?0:1));
        for(var entry:ordered) try {
            JsonObject root=JsonParser.parseString(new String(entry.getValue(),StandardCharsets.UTF_8)).getAsJsonObject();
            if(entry.getKey().equals("biomes_client.json")) {
                var biomes=root.getAsJsonObject("biomes");
                if(biomes!=null) for(var biome:biomes.entrySet()) {
                    String id=BedrockBiomes.identifier(biome.getKey());var old=biome.getValue().getAsJsonObject();
                    if(id.equals("minecraft:default"))publish(sink,"legacy",id,old,entry.getKey());
                    JsonObject components=new JsonObject();
                    legacyField(old,components,"sky_color","sky_color","sky_color");
                    legacyField(old,components,"water_surface_color","water_appearance","surface_color");
                    legacyField(old,components,"fog_identifier","fog_appearance","fog_identifier");
                    JsonObject definition=new JsonObject();definition.add("components",components);
                    publish(sink,"client_biome",id,definition,entry.getKey());
                }
            }
            for(String type:List.of("client_biome","fog_settings","atmosphere_settings","lighting_settings")) {
                var definition=root.getAsJsonObject("minecraft:"+type);
                if(definition==null)continue;
                String id=definition.getAsJsonObject("description").get("identifier").getAsString();
                if(type.equals("client_biome"))id=BedrockBiomes.identifier(id);
                publish(sink,type,id,definition,entry.getKey());
            }
        } catch(RuntimeException ex){failures.add(entry.getKey()+": "+ex.getMessage());}
        if(!failures.isEmpty())throw new IOException(String.join("; ",failures));
    }
    private static void legacyField(JsonObject old,JsonObject components,String source,String component,String target){
        if(!old.has(source))return;JsonObject value=new JsonObject();value.add(target,old.get(source));components.add("minecraft:"+component,value);
    }
    private static void publish(GeneratedResourceSink sink,String type,String id,JsonObject definition,String source)throws IOException {
        JsonObject envelope=new JsonObject();envelope.addProperty("type",type);envelope.addProperty("id",id);
        envelope.addProperty("source",source);envelope.add("definition",definition);
        boolean global=source.equals("atmospherics/atmospherics.json")||source.equals("lighting/global.json");
        String key=HexFormat.of().formatHex((type+"/"+(global?"@global":id)).getBytes(StandardCharsets.UTF_8));
        sink.put(Identifier.fromNamespaceAndPath("bedrock_native","environment/"+key+".json"),envelope.toString().getBytes(StandardCharsets.UTF_8));
    }
}
