package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Authored texture/index egg presentation. Does not register items or server entities. */
public final class BedrockSpawnEgg {
    private BedrockSpawnEgg() {}
    public static void translate(JsonObject description,Map<String,byte[]> content,GeneratedResourceSink sink) throws IOException {
        if(!description.has("spawn_egg"))return;
        var egg=description.getAsJsonObject("spawn_egg");
        // Color-only eggs need Bedrock's base/overlay masks, not recoloring a Java species egg.
        if(!egg.has("texture"))throw new IOException("Color-only spawn_egg requires unsupported Bedrock base/overlay masks");
        byte[] atlas=content.get("textures/item_texture.json");
        if(atlas==null)throw new IOException("spawn_egg texture requires textures/item_texture.json");
        var data=JsonParser.parseString(new String(atlas,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("texture_data");
        String alias=egg.get("texture").getAsString();
        if(data==null||!data.has(alias))throw new IOException("Missing spawn_egg atlas alias: "+alias);
        var texture=data.get(alias);
        if(texture.isJsonObject())texture=texture.getAsJsonObject().get("textures");
        int index=egg.has("texture_index")?egg.get("texture_index").getAsInt():0;
        if(index<0)throw new IOException("Negative spawn_egg texture_index");
        if(texture!=null&&texture.isJsonArray()) {
            if(index>=texture.getAsJsonArray().size())throw new IOException("spawn_egg texture_index out of range");
            texture=texture.getAsJsonArray().get(index);
        } else if(index!=0)throw new IOException("spawn_egg scalar texture only supports index zero");
        if(texture==null||!texture.isJsonPrimitive())throw new IOException("Unsupported spawn_egg texture entry");
        var resolved=VanillaTextureTranslator.resolveTexture(texture,content);
        if(resolved==null||resolved.data().length==0)throw new IOException("Missing spawn_egg image: "+texture);
        var entity=Identifier.parse(dev.matanyah.bedrockcompat.bedrock.entity.BedrockEntityIds.javaIdentifier(description.get("identifier").getAsString()));
        var id=Identifier.fromNamespaceAndPath(entity.getNamespace(),entity.getPath()+"_spawn_egg");
        String model=id.getNamespace()+":item/"+id.getPath();
        sink.put(Identifier.fromNamespaceAndPath(id.getNamespace(),"textures/item/"+id.getPath()+".png"),resolved.data());
        sink.put(Identifier.fromNamespaceAndPath(id.getNamespace(),"models/item/"+id.getPath()+".json"),
            ("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\""+model+"\"}}").getBytes(StandardCharsets.UTF_8));
        sink.put(Identifier.fromNamespaceAndPath(id.getNamespace(),"items/"+id.getPath()+".json"),
            ("{\"model\":{\"type\":\"minecraft:model\",\"model\":\""+model+"\"}}").getBytes(StandardCharsets.UTF_8));
    }
}
