package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockGeometryResolverTest {
    private static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    private static Map<String,BedrockGeometry> resolve(Map<String,JsonObject> sources) {
        return BedrockGeometryResolver.resolve(sources,error->fail(error));
    }

    @Test void crossFileForwardReferencesMergeBonePropertiesAndRetainUnchangedParents() {
        var base=json("""
            {"texturewidth":64,"textureheight":32,"bones":[
             {"name":"root","pivot":[2,3,4],"cubes":[{"size":[1,2,3]}]},
             {"name":"hat","parent":"root","neverRender":true,"pivot":[0,12,0],
              "cubes":[{"size":[8,8,8]}],"locators":{"tip":[0,20,0]}}]}
            """);
        var child=json("""
            {"bedrock_parent":"geometry.base","textureheight":64,"bones":[
             {"name":"hat","neverRender":false},
             {"name":"new_bone","parent":"hat","cubes":[{"size":[2,2,2]}]}]}
            """);
        String original=base.toString();Map<String,JsonObject> input=new LinkedHashMap<>();
        input.put("geometry.child",child);input.put("geometry.base",base);
        var models=resolve(input);var derived=models.get("geometry.child");
        assertEquals(64,derived.textureWidth());assertEquals(64,derived.textureHeight());
        assertEquals(3,derived.bones().size());var hat=derived.bones().get(1);
        assertEquals("root",hat.parent());assertEquals(new BedrockGeometry.Vec(0,12,0),hat.pivot());
        assertEquals(1,hat.cubes().size());assertTrue(hat.locators().containsKey("tip"));assertFalse(hat.neverRender());
        assertTrue(models.get("geometry.base").bones().get(1).neverRender());
        assertEquals(original,base.toString());assertFalse(child.has("description"));
    }

    @Test void arraysReplaceRatherThanAppendAndExplicitEmptyArrayClearsInheritedCubes() {
        var base=json("{\"bones\":[{\"name\":\"body\",\"cubes\":[{\"size\":[1,1,1]}]}]}");
        var replace=json("{\"bedrock_parent\":\"geometry.base\",\"bones\":[{\"name\":\"body\",\"cubes\":[{\"size\":[2,2,2]}]}]}");
        var clear=json("{\"bedrock_parent\":\"geometry.replace\",\"bones\":[{\"name\":\"body\",\"cubes\":[]}]}");
        var models=resolve(Map.of("geometry.base",base,"geometry.replace",replace,"geometry.clear",clear));
        assertEquals(1,models.get("geometry.replace").bones().getFirst().cubes().size());
        assertEquals(new BedrockGeometry.Vec(2,2,2),models.get("geometry.replace").bones().getFirst().cubes().getFirst().size());
        assertTrue(models.get("geometry.clear").bones().getFirst().cubes().isEmpty());
    }

    @Test void legacyDimensionsCanOverrideModernBaseWithoutLosingUnchangedAxis() {
        var models=resolve(Map.of("geometry.base",json("{\"description\":{\"texture_width\":32,\"texture_height\":16}}"),
            "geometry.child",json("{\"bedrock_parent\":\"geometry.base\",\"texturewidth\":128}")));
        assertEquals(128,models.get("geometry.child").textureWidth());
        assertEquals(16,models.get("geometry.child").textureHeight());
    }

    @Test void cyclesMissingAncestorsAndMalformedBonesAreIsolatedFromValidModels() {
        List<String> errors=new ArrayList<>();
        var models=BedrockGeometryResolver.resolve(Map.of(
            "geometry.a",json("{\"bedrock_parent\":\"geometry.b\"}"),
            "geometry.b",json("{\"bedrock_parent\":\"geometry.a\"}"),
            "geometry.missing",json("{\"bedrock_parent\":\"geometry.absent\"}"),
            "geometry.duplicate",json("{\"bones\":[{\"name\":\"x\"},{\"name\":\"x\"}]}"),
            "geometry.valid",json("{\"bones\":[{\"name\":\"valid\"}]}")),errors::add);
        assertEquals(Set.of("geometry.valid"),models.keySet());assertEquals(4,errors.size());
        assertTrue(errors.stream().anyMatch(e->e.contains("Cyclic geometry")));
        assertTrue(errors.stream().anyMatch(e->e.contains("Missing geometry parent")));
        assertTrue(errors.stream().anyMatch(e->e.contains("duplicate bone")));
    }

    @Test void depthLimitIsIndependentOfSourceIterationOrderAndCachedParents() {
        for(boolean reverse:List.of(false,true)) {
            Map<String,JsonObject> sources=new LinkedHashMap<>();
            for(int n=0;n<66;n++) {
                int i=reverse?65-n:n;
                sources.put("geometry."+i,json(i==0?"{}":"{\"bedrock_parent\":\"geometry."+(i-1)+"\"}"));
            }
            List<String> errors=new ArrayList<>();var models=BedrockGeometryResolver.resolve(sources,errors::add);
            assertFalse(models.containsKey("geometry.65"));
            assertEquals(64,models.size());assertEquals(2,errors.size());
            assertTrue(errors.stream().anyMatch(e->e.contains("64 levels")));
        }
    }

    @Test void neverRenderHidesOnlyOwnMeshNotChildHierarchyOrLocators() {
        var geometry=BedrockGeometry.parse(json("""
            {"bones":[{"name":"hidden","neverRender":true,"pivot":[2,0,0],"cubes":[{"size":[2,2,2]}],
             "locators":{"anchor":[3,0,0]}},{"name":"child","parent":"hidden","cubes":[{"size":[1,1,1]}]}]}
            """));
        var poses=Map.of("hidden",new BedrockAnimation.Pose(new BedrockAnimation.Vector(5,0,0),
            new BedrockAnimation.Vector(0,0,0),new BedrockAnimation.Vector(1,1,1)));
        var mesh=BedrockGeometryMesh.bake(geometry,poses,List.of(new BedrockRenderController.Visibility("*",true)));
        assertEquals(6,mesh.size());assertTrue(mesh.stream().allMatch(q->q.bone().equals("child")));
        assertEquals(5,mesh.stream().flatMap(q->q.vertices().stream()).mapToDouble(v->v.x()).min().orElseThrow(),1e-5);
        assertEquals(8,BedrockGeometryMesh.anchors(geometry,poses).get("anchor").m30(),1e-5);
    }
    @Test void inheritedAggregateBoneBudgetCannotBeBypassedBySmallChildDocument() {
        JsonArray bones=new JsonArray();
        for(int i=0;i<4096;i++){JsonObject bone=new JsonObject();bone.addProperty("name","b"+i);bones.add(bone);}
        JsonObject base=new JsonObject();base.add("bones",bones);
        List<String> errors=new ArrayList<>();
        var models=BedrockGeometryResolver.resolve(Map.of("geometry.base",base,"geometry.child",
            json("{\"bedrock_parent\":\"geometry.base\",\"bones\":[{\"name\":\"extra\"}]}")),errors::add);
        assertTrue(models.containsKey("geometry.base"));assertFalse(models.containsKey("geometry.child"));
        assertEquals(1,errors.size());assertTrue(errors.getFirst().contains("Too many inherited bones"));
    }
}
