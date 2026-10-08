package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockGeometryTest {
    private BedrockGeometry parse(String json) { return BedrockGeometry.parse(JsonParser.parseString(json).getAsJsonObject()); }

    @Test void preservesNamesHierarchyLocatorsAndCubeOverrides() {
        var geometry = parse("""
            {"description":{"texture_width":128,"texture_height":64},"bones":[
              {"name":"child:original","parent":"root","mirror":true,"inflate":0.2,
               "pivot":[2,3,4],"locators":{"hand":{"offset":[1,2,3],"rotation":[0,90,0]}},
               "cubes":[{"origin":[1,2,3],"size":[2,4,6],"mirror":false,"uv":[0,0]}]},
              {"name":"root"}]}
            """);
        var bone = geometry.bones().getFirst();
        assertEquals(128, geometry.textureWidth());
        assertEquals("child:original", bone.name());
        assertEquals("root", bone.parent());
        assertEquals(new BedrockGeometry.Vec(2,4,6), bone.cubes().getFirst().pivot());
        assertFalse(bone.cubes().getFirst().mirror());
        assertEquals(0.2, bone.cubes().getFirst().inflate());
        assertEquals(90, bone.locators().get("hand").rotation().y());
        assertThrows(UnsupportedOperationException.class, () -> geometry.bones().clear());
    }

    @Test void retainsSignedPerFaceUvsAndDoesNotInventAbsentFaces() {
        var cube = parse("""
            {"bones":[{"name":"a","cubes":[{"size":[2,4,6],"uv":{
            "north":{"uv":[8,16],"uv_size":[-2,4],"material_instance":"glow"}}}]}]}
            """).bones().getFirst().cubes().getFirst();
        assertEquals(1, cube.faces().size());
        assertEquals(-2, cube.faces().get("north").width());
        assertEquals("glow", cube.faces().get("north").material());
    }

    @Test void rejectsInvalidHierarchyAndNumbers() {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"bones\":[{\"name\":\"a\",\"parent\":\"b\"},{\"name\":\"b\",\"parent\":\"a\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"bones\":[{\"name\":\"a\",\"parent\":\"missing\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"bones\":[{\"name\":\"a\"},{\"name\":\"a\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"texturewidth\":0}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"bones\":[{\"name\":\"a\",\"pivot\":[1e100,0,0]}]}"));
    }

    @Test void legacyDimensionsAreAccepted() {
        assertEquals(32, parse("{\"texturewidth\":32,\"textureheight\":16}").textureWidth());
    }

    @Test void rotatedAndMirroredUvRectanglesKeepTheirOrientation() {
        var uv = new BedrockGeometry.Uv(8,16,-4,2,"*",90);
        assertArrayEquals(new double[]{8,18},uv.at(0,0));
        assertArrayEquals(new double[]{8,16},uv.at(1,0));
        assertArrayEquals(new double[]{4,16},uv.at(1,1));
        assertArrayEquals(new double[]{4,18},uv.at(0,1));
        var cube = parse("""
            {"bones":[{"name":"signed","cubes":[{"origin":[1,2,3],"size":[-2,-4,-6],
            "uv":{"north":{"uv":[8,16],"uv_rotation":180}}}]}]}
            """).bones().getFirst().cubes().getFirst();
        assertEquals(-2, cube.size().x());
        assertEquals(180, cube.faces().get("north").rotation());
    }
}
