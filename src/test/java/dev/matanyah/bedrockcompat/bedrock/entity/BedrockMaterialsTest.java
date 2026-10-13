package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockMaterialsTest {
    @Test void villagerMaskedHatsDiscardTransparentBlackInsteadOfRenderingIt() {
        var material=new BedrockMaterials(Map.of()).resolve("villager_v2_masked");
        assertTrue(material.masked());assertTrue(material.alphaTest());
        assertEquals(0,BedrockTextureComposition.masked(0,0));
    }
    @Test void packGlintAndEqualDepthAreInheritedAndCanBeOverridden() {
        var base=JsonParser.parseString("{\"bedrock_parent\":\"entity_nocull\",\"+defines\":[\"GLINT\"],\"depthFunc\":\"Equal\"}").getAsJsonObject();
        var child=JsonParser.parseString("{\"bedrock_parent\":\"base\"}").getAsJsonObject();
        var plain=JsonParser.parseString("{\"bedrock_parent\":\"child\",\"-defines\":[\"GLINT\"],\"depthFunc\":\"LessEqual\"}").getAsJsonObject();
        var materials=new BedrockMaterials(Map.of("base",base,"child",child,"plain",plain));
        assertTrue(materials.resolve("child").glint());
        assertTrue(materials.resolve("child").depthEqual());
        assertTrue(materials.resolve("child").warnings().isEmpty());
        assertFalse(materials.resolve("plain").glint());
        assertFalse(materials.resolve("plain").depthEqual());
    }
    @Test void wardenOverlayDoesNotReplaceItsOpaqueBody() {
        var materials=new BedrockMaterials(Map.of());
        assertFalse(materials.resolve("entity_nocull").cull());
        var glow=materials.resolve("warden_bioluminescent_layer");
        assertTrue(glow.blend());assertTrue(glow.additive());assertTrue(glow.emissive());
    }
    @Test void inheritedBlendSamplerAndDepthStatesSurviveResolution() {
        var base=JsonParser.parseString("""
            {"bedrock_parent":"entity_emissive","+states":["Blending","DisableDepthWrite"],
             "blendSrc":"One","blendDst":"One","+samplerStates":[{"samplerIndex":0,"textureWrap":"Repeat"}]}
            """).getAsJsonObject();
        var child=JsonParser.parseString("""
            {"bedrock_parent":"base","blendSrc":"SourceAlpha","-states":["DisableCulling"],
             "+samplerStates":[{"samplerIndex":0,"textureFilter":"Bilinear"}]}
            """).getAsJsonObject();
        var materials=new BedrockMaterials(Map.of("base",base,"child",child));
        assertFalse(materials.resolve("base").sourceAlpha());
        var material=materials.resolve("child");
        assertTrue(material.blend());assertTrue(material.additive());assertTrue(material.sourceAlpha());
        assertFalse(material.depthWrite());assertTrue(material.bilinear());assertTrue(material.repeat());assertTrue(material.cull());
        assertTrue(materials.resolve("entity_alphatest_change_color").colorMask());
    }
    @Test void enchantedArmorGlintIsInheritedAndRemovable() {
        var source=JsonParser.parseString("{\"bedrock_parent\":\"armor_enchanted\"}").getAsJsonObject();
        var removed=JsonParser.parseString("{\"bedrock_parent\":\"custom\",\"-defines\":[\"USE_GLINT\"]}").getAsJsonObject();
        var materials=new BedrockMaterials(Map.of("custom",source,"plain",removed));
        assertTrue(materials.resolve("custom").glint());
        assertTrue(materials.resolve("custom").warnings().isEmpty());
        assertFalse(materials.resolve("plain").glint());
    }
    @Test void emissiveAlphaIsACutoutLightMaskNotTranslucency() {
        var inherited=JsonParser.parseString("{\"bedrock_parent\":\"entity_emissive_alpha\",\"+defines\":[\"USE_UV_ANIM\"]}").getAsJsonObject();
        var removed=JsonParser.parseString("{\"bedrock_parent\":\"custom\",\"-defines\":[\"ALPHA_TEST\"]}").getAsJsonObject();
        var materials=new BedrockMaterials(Map.of("custom",inherited,"opaque",removed));
        var material=materials.resolve("custom");
        assertTrue(material.emissive());assertTrue(material.alphaTest());assertFalse(material.blend());
        assertFalse(materials.resolve("opaque").alphaTest());
        assertEquals(0xffffffff,BedrockTextureComposition.emissiveOpacity(0x01ffffff,true));
        assertEquals(0xfffab71d,BedrockTextureComposition.emissiveOpacity(0x02fab71d,true));
        assertEquals(0,BedrockTextureComposition.emissiveOpacity(0,true));
        assertEquals(0x009a3ee1,BedrockTextureComposition.emissiveOpacity(0x009a3ee1,true));
        assertEquals(0xff9a3ee1,BedrockTextureComposition.emissiveOpacity(0x009a3ee1,false));
    }
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
