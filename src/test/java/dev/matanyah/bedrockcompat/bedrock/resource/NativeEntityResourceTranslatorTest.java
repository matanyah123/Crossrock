package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeEntityResourceTranslatorTest {
    @Test void legacyInheritanceIsPublishedUnderCanonicalChildIdentifierWithoutMutatingSource() {
        var source=JsonParser.parseString("""
            {"format_version":"1.8.0","geometry.child:geometry.base":{"bones":[{"name":"hat","neverRender":false}]}}
            """).getAsJsonObject();
        var definitions=NativeEntityResourceTranslator.definitions(source);
        assertEquals("geometry.base",definitions.get("geometry.child").get("bedrock_parent").getAsString());
        assertFalse(definitions.containsKey("geometry.child:geometry.base"));
        assertFalse(source.getAsJsonObject("geometry.child:geometry.base").has("bedrock_parent"));
    }
    @Test void higherPackCanReplaceChildAndChooseDifferentParentUnderSameResourceIdentity() {
        var lower=NativeEntityResourceTranslator.definitions(JsonParser.parseString("""
            {"geometry.old":{"bones":[{"name":"old"}]},"geometry.child:geometry.old":{}}
            """).getAsJsonObject());
        var upper=NativeEntityResourceTranslator.definitions(JsonParser.parseString("""
            {"geometry.new":{"bones":[{"name":"new"}]},"geometry.child:geometry.new":{}}
            """).getAsJsonObject());
        var winning=new java.util.LinkedHashMap<>(lower);winning.putAll(upper);
        var models=dev.matanyah.bedrockcompat.bedrock.entity.BedrockGeometryResolver.resolve(winning,error->fail(error));
        assertEquals("new",models.get("geometry.child").bones().getFirst().name());
        assertEquals(3,winning.size());
    }
    @Test void recognizesGeometryWithArbitraryFilenamesAndRetainsBoneNames() {
        var root=JsonParser.parseString("""
            {"minecraft:geometry":[{"description":{"identifier":"geometry.test"},
            "bones":[{"name":"arbitrary_Bedrock_bone","pivot":[1,2,3]}]}]}
            """).getAsJsonObject();
        var definitions=NativeEntityResourceTranslator.definitions(root);
        assertEquals("arbitrary_Bedrock_bone",definitions.get("geometry.test").getAsJsonArray("bones")
            .get(0).getAsJsonObject().get("name").getAsString());
    }
    @Test void extractsControllersByIdentifierInsteadOfHashedFilename() {
        var root=JsonParser.parseString("""
            {"render_controllers":{"controller.render.test":{"geometry":"Geometry.default",
            "textures":["q.is_name_any('Fred') ? Texture.fred : Texture.default"]}}}
            """).getAsJsonObject();
        assertTrue(NativeEntityResourceTranslator.definitions(root).containsKey("controller.render.test"));
    }
}
