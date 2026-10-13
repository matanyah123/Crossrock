package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.resource.InMemoryGeneratedResourceStore;
import net.minecraft.resources.Identifier;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Build the union after all packs/subpacks have resolved; a later pack must not drop earlier sprites. */
final class BedrockParticleAtlas {
    static void generate(InMemoryGeneratedResourceStore store) {
        var snapshot=store.snapshot();var sources=new JsonArray();var textures=new TreeSet<String>();
        snapshot.resources().forEach((id,bytes)-> {
            if(!id.getNamespace().equals("bedrock_native")||!id.getPath().startsWith("entity_definitions/"))return;
            var envelope=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!envelope.get("id").getAsString().startsWith("particle."))return;
            var definition=envelope.getAsJsonObject("definition");
            if(!definition.has("description"))return;
            var description=definition.getAsJsonObject("description");
            if(!description.has("basic_render_parameters"))return;
            var render=description.getAsJsonObject("basic_render_parameters");
            if(!render.has("texture"))return;
            String texture=render.get("texture").getAsString().replaceFirst("^textures/","").replaceFirst("\\.(png|tga)$","");
            if(Identifier.isValidPath(texture)&&snapshot.resources().containsKey(Identifier.fromNamespaceAndPath("bedrock_native","textures/"+texture+".png")))textures.add(texture);
        });
        for(String texture:textures) {
            var source=new JsonObject();source.addProperty("type","minecraft:single");
            source.addProperty("resource","bedrock_native:"+texture);source.addProperty("sprite","bedrock_native:"+texture);sources.add(source);
        }
        if(!sources.isEmpty()) {
            var atlas=new JsonObject();atlas.add("sources",sources);
            store.put(Identifier.parse("minecraft:atlases/particles.json"),atlas.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
