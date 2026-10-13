package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockBiomesTest {
    @Test void legacyIdentifiersMatchJavaWithoutRewritingCustomNamespaces() {
        for(var pair:Map.of("hell","nether_wastes","soulsand_valley","soul_sand_valley",
                "cold_taiga_hills","snowy_taiga","mesa_bryce","eroded_badlands",
                "swampland","swamp","birch_forest_hills_mutated","old_growth_birch_forest").entrySet()) {
            var queries=new EntityQueryContext("","minecraft:"+pair.getValue(),Set.of(),Map.of());
            assertEquals(1d,queries.get("entity_biome_has_any_identifier",List.of("minecraft:"+pair.getKey())));
        }
        assertEquals("other:hell",BedrockBiomes.identifier("other:hell"));
        assertFalse(BedrockBiomes.tags("other:desert",Set.of()).contains("desert"));
    }
    @Test void vanillaAndDatapackTagsExposeBedrockVocabularyWithoutDroppingOriginalTags() {
        var q=new EntityQueryContext("","minecraft:bamboo_jungle",Set.of("example:enchanted"),Map.of());
        assertEquals(1d,q.get("entity_biome_has_all_tags",List.of("jungle","bamboo","overworld","example:enchanted")));
        assertEquals(0d,q.get("entity_biome_has_any_tags",List.of("desert","nether")));
        var custom=new EntityQueryContext("","other:woods",Set.of("minecraft:is_forest","other:magical"),Map.of());
        assertEquals(1d,custom.get("entity_biome_has_all_tags",List.of("forest","minecraft:is_forest","other:magical")));
    }
    @Test void packSettingGatesBothTextureAndGeometrySelectionOnTheSameRuntime() {
        var model=BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"test:mob","geometry":{"default":"geometry.normal","warm":"geometry.warm"},
             "textures":{"default":"textures/normal","warm":"textures/warm"},
             "scripts":{"pre_animation":["v.warm=q.is_pack_setting_selected('test:variants','enabled') && q.entity_biome_has_any_tags('desert','mesa');"]},
             "render_controllers":["controller.render.test.biome"]}}
            """).getAsJsonObject());
        var controller=new BedrockRenderController(JsonParser.parseString("""
            {"geometry":"v.warm?Geometry.warm:Geometry.default","textures":["v.warm?Texture.warm:Texture.default"]}
            """).getAsJsonObject());
        var runtime=new BedrockEntityInstance(model);double time=0;
        for(String setting:List.of("enabled","disabled","enabled"))for(String biome:List.of("desert","forest","wooded_badlands")) {
            var queries=new EntityQueryContext("","minecraft:"+biome,Set.of(),Map.of("pack_settings",Map.of("test:variants",setting)));
            var frame=runtime.evaluateFrame(queries,Map.of("controller.render.test.biome",controller),Map.of(),Map.of(),OptionalInt.empty(),time++);
            boolean warm=setting.equals("enabled")&&!biome.equals("forest");
            assertEquals(warm?"geometry.warm":"geometry.normal",frame.selections().getFirst().geometry());
            assertEquals(List.of(warm?"textures/warm":"textures/normal"),frame.selections().getFirst().textures());
        }
    }
}
