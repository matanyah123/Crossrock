package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockMaterialsTest {
    @Test void arbitraryInheritedMaterialResolvesCapabilitiesAndReportsUnsupportedFlags() {
        var source=JsonParser.parseString("""
            {"bedrock_parent":"entity_alphablend","+defines":["USE_EMISSIVE"],"-states":["DisableCulling"],"+states":["StencilWrite"]}
            """).getAsJsonObject();
        var material=new BedrockMaterials(Map.of("any_pack_name",source)).resolve("any_pack_name");
        assertTrue(material.blend());assertTrue(material.emissive());assertTrue(material.cull());
        assertTrue(material.warnings().getFirst().contains("StencilWrite"));
    }
    @Test void cyclesFailWithoutRecursionOverflow() {
        var source=JsonParser.parseString("{\"bedrock_parent\":\"cycle\"}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class,()->new BedrockMaterials(Map.of("cycle",source)).resolve("cycle"));
    }
    @Test void maskedCapabilityInheritsAndCanBeRemovedOrAddedByArbitraryMaterials() {
        var inherited=JsonParser.parseString("{\"bedrock_parent\":\"villager_v2_masked\"}").getAsJsonObject();
        var removed=JsonParser.parseString("{\"bedrock_parent\":\"custom\",\"-defines\":[\"MASKED_MULTITEXTURE\"]}").getAsJsonObject();
        var added=JsonParser.parseString("{\"+defines\":[\"MASKED_MULTITEXTURE\"]}").getAsJsonObject();
        var materials=new BedrockMaterials(Map.of("custom",inherited,"removed",removed,"added",added));
        assertTrue(materials.resolve("custom").masked());
        assertTrue(materials.resolve("custom").warnings().isEmpty());
        assertFalse(materials.resolve("removed").masked());
        assertTrue(materials.resolve("added").masked());
    }
}
