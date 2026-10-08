package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockVehicleResolutionTest {
    @Test void everyVanillaWoodResolvesToSharedBedrockEntityWithoutLosingVariant() {
        var woods=List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","bamboo","cherry","pale_oak");
        for(int i=0;i<woods.size();i++)for(boolean chest:List.of(false,true)) {
            String wood=woods.get(i),kind=wood.equals("bamboo")?"raft":"boat";
            String id="minecraft:"+wood+(chest?"_chest_":"_")+kind;
            var boat=BedrockEntityIds.boat(id).orElseThrow();
            assertEquals(i,boat.variant());assertEquals(wood,boat.wood());assertEquals(chest,boat.chest());
            assertEquals(chest?"minecraft:chest_boat":"minecraft:boat",BedrockEntityIds.bedrock(id));
            assertEquals("minecraft:textures/entity/"+(chest?"chest_boat/":"boat/")+wood+".png",boat.javaTexture());
            assertEquals(120,BedrockEntityIds.bodyYaw(id,30));
        }
        assertEquals("example:oak_boat",BedrockEntityIds.bedrock("example:oak_boat"));
        assertEquals("minecraft:unknown_boat",BedrockEntityIds.bedrock("minecraft:unknown_boat"));
        assertEquals(30,BedrockEntityIds.bodyYaw("minecraft:armor_stand",30));
    }
    @Test void packAuthoredVariantArraySelectsTextureUsingAdaptedState() {
        var controller=new BedrockRenderController(JsonParser.parseString("""
            {"arrays":{"textures":{"Array.boats":["Texture.oak","Texture.spruce","Texture.birch","Texture.jungle",
             "Texture.acacia","Texture.dark_oak","Texture.mangrove","Texture.bamboo","Texture.cherry","Texture.pale_oak"]}},
             "geometry":"Geometry.default","textures":["Array.boats[q.variant]"],"materials":[{"*":"Material.default"}]}
            """).getAsJsonObject());
        Map<String,Object> aliases=new HashMap<>();aliases.put("geometry.default","geometry.boat");aliases.put("material.default","entity");
        for(String wood:List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","bamboo","cherry","pale_oak")) {
            aliases.put("texture."+wood,"textures/"+wood);
        }
        for(String id:List.of("minecraft:birch_boat","minecraft:bamboo_raft","minecraft:cherry_chest_boat")) {
            var boat=BedrockEntityIds.boat(id).orElseThrow();
            var queries=new EntityQueryContext("","",Set.of(),Map.of("variant",(double)boat.variant()));
            assertEquals(List.of("textures/"+boat.wood()),controller.select(new Molang.Context(queries,0),aliases).textures());
        }
    }
    @Test void indexedRiderQueriesUseImmutableSnapshotAndInvalidSeatsReturnZero() {
        var queries=new EntityQueryContext("","",Set.of(),Map.of("riders",List.of(
            Map.of("body_y_rotation",90d,"head_y_rotation",120d),Map.of("body_x_rotation",15d))));
        assertEquals(90d,queries.get("rider_body_y_rotation",List.of(0d)));
        assertEquals(120d,queries.get("rider_head_y_rotation",List.of()));
        assertEquals(15d,queries.get("rider_body_x_rotation",List.of(1d)));
        for(double index:List.of(-1d,.5,2d,Double.NaN,Double.POSITIVE_INFINITY))
            assertEquals(0d,queries.get("rider_body_y_rotation",List.of(index)));
        assertEquals(0d,queries.get("rider_head_x_rotation",List.of(1d)));
        assertThrows(IllegalArgumentException.class,()->queries.get("rider_head_y_rotation",List.of(0d,1d)));
    }
    @Test void ridingQueriesAcceptSharedBedrockRaftAndLivingVehicleIdentifiers() {
        for(String id:List.of("minecraft:bamboo_raft","minecraft:bamboo_chest_raft","minecraft:oak_boat",
                "minecraft:birch_chest_boat","minecraft:villager")) {
            Set<String> types=new HashSet<>();types.add(id);types.add(BedrockEntityIds.bedrock(id));
            var queries=new EntityQueryContext("","",Set.of(),Map.of("vehicle_types",Set.copyOf(types)));
            assertEquals(1d,queries.get("is_riding_any_entity_of_type",List.of(id)));
            assertEquals(1d,queries.get("is_riding_any_entity_of_type",List.of(BedrockEntityIds.bedrock(id))));
            assertEquals(0d,queries.get("is_riding_any_entity_of_type",List.of("example:bamboo_raft")));
        }
    }
}
