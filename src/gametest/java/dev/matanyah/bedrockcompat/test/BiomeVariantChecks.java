package dev.matanyah.bedrockcompat.test;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.client.entity.NativeRenderState;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.nio.file.*;

final class BiomeVariantChecks {
    static void fixture(Path pack)throws Exception {
        var manifest=JsonParser.parseString(Files.readString(pack.resolve("manifest.json"))).getAsJsonObject();
        var settings=new JsonArray();var setting=new JsonObject();
        setting.addProperty("type","toggle");setting.addProperty("text","Biome variants");
        setting.addProperty("name","fixture:biome_variants");setting.addProperty("default",true);settings.add(setting);
        manifest.add("settings",settings);Files.writeString(pack.resolve("manifest.json"),manifest.toString());
        Files.writeString(pack.resolve("entity/biome_rabbit.json"),"""
            {"minecraft:client_entity":{"description":{"identifier":"minecraft:rabbit",
             "materials":{"default":"entity_alphatest"},"geometry":{"default":"geometry.test_stand"},
             "textures":{"default":"textures/entity/armor_stand","warm":"textures/entity/emissive_fixture"},
             "render_controllers":["controller.render.fixture.biome"],
             "scripts":{"pre_animation":["v.warm=q.is_pack_setting_enabled('fixture:biome_variants')&&q.entity_biome_has_all_tags('desert','overworld');"]}}}}
            """);
        Files.writeString(pack.resolve("render_controllers/biome.json"),"""
            {"render_controllers":{"controller.render.fixture.biome":{"geometry":"Geometry.default",
            "textures":["v.warm?Texture.warm:Texture.default"],"materials":[{"*":"Material.default"}]}}}
            """);
        Files.createDirectories(pack.resolve("biomes"));
        Files.writeString(pack.resolve("biomes/desert.json"),"""
            {"minecraft:biome":{"description":{"identifier":"desert"},
             "components":{"minecraft:tags":{"tags":["fixture:custom_desert"]}}}}
            """);
    }
    static void verify(ClientGameTestContext context,String expected) {
        context.waitTicks(4);
        context.runOnClient(mc->{
            var pig=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false)
                .filter(e->e instanceof net.minecraft.world.entity.animal.rabbit.Rabbit).findFirst().orElseThrow();
            var state=mc.getEntityRenderDispatcher().getRenderer(pig).createRenderState(pig,1);
            var frame=((NativeRenderState)state).bedrockCompatFrame();
            if(frame==null||!frame.draws().getFirst().texture().getPath().equals(expected))
                throw new AssertionError("Biome/config texture selection failed: "+frame);
            if(!dev.matanyah.bedrockcompat.client.entity.NativeEntityResources.snapshot().biomeTags()
                    .getOrDefault("minecraft:desert",java.util.Set.of()).contains("fixture:custom_desert"))
                throw new AssertionError("Resource-owned biome tags were not imported");
        });
    }
}
