package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Legacy RP and modern BP item presentation, independently of attachable rendering/gameplay. */
public final class BedrockItemAssetTranslator implements BedrockStackResourceTranslator {
    private final BedrockPackContentReader reader;
    private final Function<String,Optional<Identifier>> targets;
    public BedrockItemAssetTranslator(BedrockPackContentReader reader){this(reader,id->Optional.ofNullable(Identifier.tryParse(id)));}
    public BedrockItemAssetTranslator(BedrockPackContentReader reader,Function<String,Optional<Identifier>> targets){this.reader=reader;this.targets=targets;}
    @Override public void translate(BedrockPack pack,BedrockPackConfiguration config,GeneratedResourceSink sink) {
        List<String> errors=new ArrayList<>();translateStack(List.of(new BedrockTranslationPipeline.ConfiguredPack(pack,config)),sink,errors);
        if(!errors.isEmpty())throw new IllegalArgumentException(String.join("; ",errors));
    }
    @Override public void translateStack(List<BedrockTranslationPipeline.ConfiguredPack> stack,GeneratedResourceSink sink,List<String> errors) {
        Map<String,byte[]> assets=new LinkedHashMap<>();Map<String,JsonObject> definitions=new LinkedHashMap<>();JsonObject atlas=new JsonObject();
        for(var selected:stack.reversed()) {
            var type=selected.pack().type();if(type!=BedrockPackType.RESOURCE&&type!=BedrockPackType.BEHAVIOR&&type!=BedrockPackType.MIXED)continue;
            boolean resource=type!=BedrockPackType.BEHAVIOR;
            try{reader.visitBlockAssets(selected.pack(),selected.configuration().selectedSubpack(),(path,data)->{
                String p=path.replace('\\','/').toLowerCase(Locale.ROOT);
                try {
                    if(p.startsWith("items/")&&p.endsWith(".json")) {
                        var root=parse(data);var item=object(root,"minecraft:item");
                        if(item!=null)definitions.put(item.getAsJsonObject("description").get("identifier").getAsString(),item);
                    }else if(resource&&p.equals("textures/item_texture.json")) {
                        var tiles=object(parse(data),"texture_data");if(tiles!=null)tiles.entrySet().forEach(e->atlas.add(e.getKey(),e.getValue()));
                    }else if(resource&&p.startsWith("textures/"))assets.put(p,data);
                }catch(RuntimeException ex){errors.add(selected.pack().displayName()+" "+p+": "+ex.getMessage());}
            });}catch(IOException ex){errors.add(selected.pack().displayName()+": "+ex.getMessage());}
        }
        for(var entry:definitions.entrySet()) {
            var components=object(entry.getValue(),"components");if(components==null||!components.has("minecraft:icon"))continue;
            try {
                Identifier target=targets.apply(entry.getKey()).orElseThrow(()->new IllegalArgumentException("No matching Java item identity"));
                JsonElement icon=components.get("minecraft:icon");String alias;
                if(icon.isJsonPrimitive()&&icon.getAsJsonPrimitive().isString())alias=icon.getAsString();
                else {
                    var object=icon.getAsJsonObject();
                    if(object.has("textures")) {
                        var textures=object.getAsJsonObject("textures");
                        if(textures.size()!=1||!textures.has("default"))throw new IllegalArgumentException("Dynamic/trim icon channels need an item context mapping");
                        alias=textures.get("default").getAsString();
                    }else if(object.has("texture"))alias=object.get("texture").getAsString();
                    else if(object.has("icon_name"))alias=object.get("icon_name").getAsString();
                    else throw new IllegalArgumentException("Unknown item icon form");
                }
                if(!atlas.has(alias))throw new IllegalArgumentException("Missing item atlas alias "+alias);
                var tile=atlas.getAsJsonObject(alias);JsonElement path=tile.get("textures");
                if(path!=null&&path.isJsonArray()&&path.getAsJsonArray().size()==1)path=path.getAsJsonArray().get(0);
                if(path==null||!path.isJsonPrimitive()||!path.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Indexed/weighted item texture needs an aux mapping");
                var texture=VanillaTextureTranslator.resolveTexture(path,assets);if(texture==null||texture.data().length==0)throw new IllegalArgumentException("Missing item image "+path);
                try(var in=new ByteArrayInputStream(texture.data())){if(javax.imageio.ImageIO.read(in)==null)throw new IllegalArgumentException("Unreadable item image");}
                String model="item/assets/"+target.getNamespace()+"/"+target.getPath();
                Map<Identifier,byte[]> pending=new LinkedHashMap<>();
                pending.put(Identifier.fromNamespaceAndPath("bedrock_compat","textures/"+model+".png"),texture.data());
                JsonObject textures=new JsonObject();textures.addProperty("layer0","bedrock_compat:"+model);
                JsonObject unbaked=new JsonObject();unbaked.addProperty("parent","minecraft:item/generated");unbaked.add("textures",textures);
                if(components.has("minecraft:render_offsets"))errors.add(entry.getKey()+": legacy render_offsets preset retained as Java item transform fallback");
                put(pending,Identifier.fromNamespaceAndPath("bedrock_compat","models/"+model+".json"),unbaked);
                JsonObject selected=new JsonObject();selected.addProperty("type","minecraft:model");selected.addProperty("model","bedrock_compat:"+model);
                JsonObject item=new JsonObject();item.add("model",selected);put(pending,Identifier.fromNamespaceAndPath(target.getNamespace(),"items/"+target.getPath()+".json"),item);
                pending.forEach(sink::put);
            }catch(RuntimeException|IOException ex){errors.add(entry.getKey()+" item visual: "+ex.getMessage()+"; retaining Java/lower item fallback");}
        }
    }
    private static JsonObject parse(byte[] data){return JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static JsonObject object(JsonObject o,String key){return o.has(key)&&o.get(key).isJsonObject()?o.getAsJsonObject(key):null;}
    private static void put(Map<Identifier,byte[]> out,Identifier id,JsonObject json){out.put(id,json.toString().getBytes(StandardCharsets.UTF_8));}
}
