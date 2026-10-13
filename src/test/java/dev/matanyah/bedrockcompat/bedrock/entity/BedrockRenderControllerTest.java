package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockRenderControllerTest {
    @Test void omittedMaterialsUseTheAttachableDefault() {
        var controller=new BedrockRenderController(JsonParser.parseString("""
            {"geometry":"Geometry.default","textures":["Texture.default"]}
            """).getAsJsonObject());
        var selected=controller.select(new Molang.Context((q,a)->0d),Map.of(
            "geometry.default","geometry.egg","texture.default","textures/egg","material.default","entity_emissive_alpha"));
        assertEquals(List.of(new BedrockRenderController.Material("*","entity_emissive_alpha")),selected.materials());
    }
    @Test void bareArraysConcatenateInOrderWithForwardReferencesDuplicatesAndEmptyMembers() {
        var controller=new BedrockRenderController(JsonParser.parseString("""
            {"arrays":{"textures":{
                "Array.all":["Array.pair","Texture.c"," ARRAY.PAIR ","Array.empty"],
                "Array.pair":["Texture.a","Texture.b"],"Array.empty":[]}},
             "geometry":"Geometry.default","textures":["Array.all[q.variant]"]}
            """).getAsJsonObject());
        var aliases=Map.<String,Object>of("geometry.default","geometry.test",
            "texture.a","a","texture.b","b","texture.c","c");
        String[] expected={"a","b","c","a","b","a"};
        for(int i=0;i<expected.length;i++) {
            final int index=i;
            assertEquals(List.of(expected[i]),controller.select(new Molang.Context((q,a)->(double)index),aliases).textures());
        }
        assertEquals(List.of("a"),controller.select(new Molang.Context((q,a)->-1d),aliases).textures());
    }

    @Test void concatenatedEntriesStayLazyAndUseTheCurrentEntityContext() {
        var controller=new BedrockRenderController(JsonParser.parseString("""
            {"arrays":{"textures":{"Array.all":["Array.leaf","Texture.other"],
                "Array.leaf":["q.variant ? Texture.a : Texture.b"]}},
             "geometry":"Geometry.default","textures":["Array.all[v.index]"]}
            """).getAsJsonObject());
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var context=new Molang.Context((q,a)->{calls.incrementAndGet();return 1d;});
        var aliases=Map.<String,Object>of("geometry.default","geometry.test",
            "texture.a","a","texture.b","b","texture.other","other");
        context.setVariable("index",1d);
        assertEquals(List.of("other"),controller.select(context,aliases).textures());
        assertEquals(0,calls.get());
        context.setVariable("index",0d);
        assertEquals(List.of("a"),controller.select(context,aliases).textures());
        assertEquals(1,calls.get());
    }

    @Test void concatenationRejectsCyclesAndCrossTypeOrMissingReferences() {
        for(String arrays:List.of(
            "{\"textures\":{\"Array.all\":[\"Array.all\"]}}",
            "{\"textures\":{\"Array.all\":[\"Array.b\"],\"Array.b\":[\"Array.all\"]}}",
            "{\"textures\":{\"Array.all\":[\"Array.missing\"]}}",
            "{\"textures\":{\"Array.all\":[\"Array.geo\"]},\"geometries\":{\"Array.geo\":[\"Geometry.default\"]}}")) {
            assertThrows(IllegalArgumentException.class,()->new BedrockRenderController(JsonParser.parseString(
                "{\"geometry\":\"Geometry.default\",\"textures\":[\"Array.all[0]\"],\"arrays\":"+arrays+"}").getAsJsonObject()));
        }
    }

    @Test void concatenationBoundsDepthAndExponentialExpansion() {
        for(boolean exponential:List.of(false,true)) {
            var document=JsonParser.parseString("{\"geometry\":\"Geometry.default\",\"textures\":[]}").getAsJsonObject();
            var arrays=new com.google.gson.JsonObject();var textures=new com.google.gson.JsonObject();
            arrays.add("textures",textures);document.add("arrays",arrays);
            int depth=exponential?18:66;
            for(int i=0;i<depth;i++) {
                var members=new com.google.gson.JsonArray();
                members.add(i==depth-1?"Texture.default":"Array.a"+(i+1));
                if(exponential&&i<depth-1)members.add("Array.a"+(i+1));
                textures.add("Array.a"+i,members);
            }
            var error=assertThrows(IllegalArgumentException.class,()->new BedrockRenderController(document));
            assertTrue(error.getMessage().contains(exponential?"budget":"nesting"),error.getMessage());
        }
    }

    @Test void alreadyResolvedArraysCannotBypassNestingLimit() {
        var document=JsonParser.parseString("{\"geometry\":\"Geometry.default\",\"textures\":[]}").getAsJsonObject();
        var arrays=new com.google.gson.JsonObject();var textures=new com.google.gson.JsonObject();
        arrays.add("textures",textures);document.add("arrays",arrays);
        for(int i=0;i<66;i++) {
            var members=new com.google.gson.JsonArray();
            members.add(i==0?"Texture.default":"Array.a"+(i-1));
            textures.add("Array.a"+i,members);
        }
        var error=assertThrows(IllegalArgumentException.class,()->new BedrockRenderController(document));
        assertTrue(error.getMessage().contains("nesting"));
    }

    @Test void sharedArrayNamesResolveWithinTheirResourceTypeAndRestoreCallerBindings() {
        var controller=new BedrockRenderController(JsonParser.parseString("""
            {"arrays":{
                "geometries":{"Array.shared":["Array.nested[0]"],"Array.nested":["Geometry.default"]},
                "textures":{"Array.shared":["Texture.default"]},
                "materials":{"Array.shared":["Material.default"]}},
             "geometry":"Array.shared[0]","textures":["Array.shared[0]"],
             "materials":[{"*":"Array.shared[0]"}]}
            """).getAsJsonObject());
        var context=new Molang.Context((q,a)->0d);
        context.bind(Map.of("array.shared",List.of("caller")));
        var selected=controller.select(context,Map.of("geometry.default","geometry.test",
            "texture.default","textures/test","material.default","entity_alphatest"));
        assertEquals("geometry.test",selected.geometry());
        assertEquals(List.of("textures/test"),selected.textures());
        assertEquals("entity_alphatest",selected.materials().getFirst().identifier());
        assertEquals("caller",Molang.compile("array.shared[0]").evaluate(context));
    }

    @Test void failedArrayEvaluationRestoresCallerAndDoesNotCrossResourceTypes() {
        var controller=new BedrockRenderController(JsonParser.parseString("""
            {"arrays":{"textures":{"Array.only_texture":["Texture.default"]}},
             "geometry":"Array.only_texture[0]","textures":["Texture.default"]}
            """).getAsJsonObject());
        var context=new Molang.Context((q,a)->0d);
        context.bind(Map.of("texture.caller","original"));
        assertThrows(IllegalArgumentException.class,()->controller.select(context,
            Map.of("texture.default","textures/test")));
        assertEquals("original",Molang.compile("texture.caller").evaluate(context));
    }

    @Test void visibilityCanCompareAnUnambiguousTextureArray() {
        var controller=new BedrockRenderController(JsonParser.parseString("""
            {"arrays":{"textures":{"Array.skins":["Texture.default"]}},
             "geometry":"Geometry.default","textures":["Array.skins[0]"],
             "part_visibility":[{"hat":"Array.skins[0] == Texture.default"}]}
            """).getAsJsonObject());
        var selected=controller.select(new Molang.Context((q,a)->0d),
            Map.of("geometry.default","geometry.test","texture.default","textures/test"));
        assertTrue(selected.visibility().getFirst().visible());
    }

    @Test void colorsOverlayAndUvAnimationEvaluateAgainstFrameQueries() {
        var controller=new BedrockRenderController(JsonParser.parseString("""
            {"geometry":"Geometry.default","textures":["Texture.default"],"color":{"r":0.5},
             "overlay_color":{"r":1,"g":0,"b":0,"a":0.5},"is_hurt_color":{"r":0,"g":1,"b":0,"a":0.5},
             "uv_anim":{"offset":["q.life_time*0.25",0],"scale":[2,3]},"ignore_lighting":true}
            """).getAsJsonObject());
        var context=new Molang.Context((q,a)->q.equals("life_time")?4d:1d);
        var result=controller.select(context,Map.of("geometry.default","geometry.test","texture.default","textures/test"));
        var appearance=result.appearance();
        assertEquals(.375,appearance.color().r());assertEquals(.75,appearance.color().g());
        assertEquals(.25,appearance.color().b());assertEquals(1,appearance.color().a());
        assertEquals(1,appearance.offsetU());assertEquals(2,appearance.scaleU());assertEquals(3,appearance.scaleV());
        assertTrue(appearance.fullBright());assertTrue(appearance.replaceHurtOverlay());
    }
    @Test void choosesPackGeometryFromPoseWithoutRenamingBones() {
        var controller = new BedrockRenderController(JsonParser.parseString("""
            {"arrays":{"geometries":{"Array.forms":["Geometry.default","Geometry.variant"]}},
             "geometry":"Array.forms[variable.armor_stand.pose_index]",
             "textures":["query.is_name_any('Special') ? Texture.special : Texture.default"],
             "materials":[{"*":"Material.default"}],
             "part_visibility":[{"arbitrary_pack_bone":"variable.show_prop"}]}
            """).getAsJsonObject());
        var context = new Molang.Context((name,args) -> name.equals("is_name_any") && args.contains("Special") ? 1d : 0d);
        context.setVariable("armor_stand.pose_index", 3d);
        context.setVariable("show_prop", 1d);
        var result = controller.select(context, Map.of(
            "geometry.default", "geometry.base", "geometry.variant", "geometry.prop",
            "texture.default", "textures/base", "texture.special", "textures/special",
            "material.default", "entity_alphatest"));
        assertEquals("geometry.prop", result.geometry());
        assertEquals(List.of("textures/special"), result.textures());
        assertEquals("arbitrary_pack_bone", result.visibility().getFirst().bonePattern());
        assertTrue(result.visibility().getFirst().visible());
    }

    @Test void arraysClampNegativeAndWrapPositiveIndices() {
        var context = new Molang.Context((name,args) -> 0d);
        context.bind(Map.of("array.values", List.of(10d,20d,30d)));
        assertEquals(10d, Molang.compile("array.values[-1]").evaluate(context));
        assertEquals(20d, Molang.compile("array.values[4.9]").evaluate(context));
    }

    @Test void recursiveArrayFailsWithoutStackOverflowAndContextRecovers() {
        var context = new Molang.Context((name,args) -> 0d);
        context.bind(Map.of("array.cycle", List.of(Molang.compile("array.cycle[0]"))));
        assertThrows(IllegalArgumentException.class, () -> Molang.compile("array.cycle[0]").evaluate(context));
        context.bind(Map.of("array.cycle", List.of(4d)));
        assertEquals(4d, Molang.compile("array.cycle[0]").evaluate(context));
    }
}
