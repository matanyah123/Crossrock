package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockPolygonMeshTest {
    private static final String TABLES = """
        "positions":[[0,0,0],[2,0,0],[0,2,0],[2,2,0]],
        "normals":[[0,0,1],[1,0,1],[0,1,1],[0,0,1]],
        "uvs":[[0,0],[16,0],[0,8],[16,8]]
        """;
    private BedrockGeometry parse(String properties) {
        return BedrockGeometry.parse(JsonParser.parseString("""
            {"description":{"texture_width":16,"texture_height":8},"bones":[
             {"name":"parent"},{"name":"mesh","parent":"parent","poly_mesh":{
            """+properties+"}}]}").getAsJsonObject());
    }
    private List<BedrockGeometryMesh.Quad> bake(BedrockGeometry model) {
        return BedrockGeometryMesh.bake(model,Map.of(),List.of());
    }
    @Test void indexedTriangleResolvesIndependentIndicesAndPixelUvs() {
        var mesh=bake(parse(TABLES+",\"polys\":[[[0,0,0],[1,2,3],[2,1,2]]]")).getFirst();
        assertEquals(4,mesh.vertices().size());
        assertEquals(mesh.vertices().get(2),mesh.vertices().get(3));
        assertEquals(1,mesh.vertices().get(1).u());assertEquals(1,mesh.vertices().get(1).v());
        var n=mesh.vertices().get(1).normal();assertEquals(0,n.x());assertEquals(Math.sqrt(.5),n.y(),1e-6);
        assertEquals(1,mesh.nz());
    }
    @Test void sequentialQuadsAndNormalizedUvsStayAuthored() {
        var model=parse(TABLES+",\"normalized_uvs\":true,\"polys\":\"quad_list\"");
        var mesh=bake(model).getFirst();
        assertEquals(16,mesh.vertices().get(1).u()); // Never divide or clamp normalized UVs.
        assertEquals(8,mesh.vertices().get(2).v());
        assertThrows(UnsupportedOperationException.class,()->model.bones().get(1).polygons().clear());
    }
    @Test void sequentialTrianglesAndCubesCanCoexist() {
        var model=parse("\"positions\":[[0,0,0],[1,0,0],[0,1,0]],\"normals\":[[0,0,1],[0,0,1],[0,0,1]],"
            +"\"uvs\":[[0,0],[1,0],[0,1]],\"polys\":\"tri_list\"");
        assertEquals(1,bake(model).size());
        var source=JsonParser.parseString("{\"bones\":[{\"name\":\"both\",\"cubes\":[{\"size\":[1,1,1]}],"
            +"\"poly_mesh\":{"+TABLES+",\"polys\":[[[0,0,0],[1,0,1],[2,0,2]]]}}]}").getAsJsonObject();
        assertEquals(7,bake(BedrockGeometry.parse(source)).size());
    }
    @Test void animationAndInverseTransposeNormalsFollowParentHierarchy() {
        var model=parse(TABLES+",\"polys\":[[[0,0,0],[1,1,1],[2,2,2]]]");
        var pose=new BedrockAnimation.Pose(new BedrockAnimation.Vector(4,3,2),
            new BedrockAnimation.Vector(0,0,0),new BedrockAnimation.Vector(2,1,1));
        var mesh=BedrockGeometryMesh.bake(model,Map.of("parent",pose),List.of()).getFirst();
        assertEquals(4,mesh.vertices().getFirst().x());assertEquals(8,mesh.vertices().get(1).x());
        assertEquals(3,mesh.vertices().getFirst().y());assertEquals(2,mesh.vertices().getFirst().z());
        var n=mesh.vertices().get(1).normal();
        assertEquals(1/Math.sqrt(5),n.x(),1e-6);assertEquals(2/Math.sqrt(5),n.z(),1e-6);
        assertEquals(0,bake(model).getFirst().vertices().getFirst().x());
        assertTrue(BedrockGeometryMesh.bake(model,Map.of(),List.of(new BedrockRenderController.Visibility("mesh",false))).isEmpty());
    }
    @Test void negativeScaleAndCameraReflectionPreserveWindingAndSmoothNormals() {
        var model=parse(TABLES+",\"polys\":[[[0,0,0],[1,1,1],[2,2,2]]]");
        var pose=new BedrockAnimation.Pose(new BedrockAnimation.Vector(0,0,0),
            new BedrockAnimation.Vector(0,0,0),new BedrockAnimation.Vector(-1,1,1));
        var mesh=BedrockGeometryMesh.bake(model,Map.of("parent",pose),List.of());
        var quad=mesh.getFirst();assertEquals(2,quad.vertices().getFirst().y());
        assertTrue(quad.vertices().get(1).normal().x()<0);
        var reflected=BedrockGeometryMesh.reflectDepth(mesh).getFirst();
        assertEquals(-quad.vertices().getLast().normal().z(),reflected.vertices().getFirst().normal().z());
        assertEquals(mesh,BedrockGeometryMesh.reflectDepth(BedrockGeometryMesh.reflectDepth(mesh)));
    }
    @Test void zeroNormalsAndSingularTransformsNeverProduceNanLighting() {
        var model=parse(TABLES.replace("[0,0,1]","[0,0,0]")+",\"polys\":[[[0,0,0],[1,0,1],[2,0,2]]]");
        var pose=new BedrockAnimation.Pose(new BedrockAnimation.Vector(0,0,0),
            new BedrockAnimation.Vector(0,0,0),new BedrockAnimation.Vector(1,1,0));
        var quad=BedrockGeometryMesh.bake(model,Map.of("parent",pose),List.of()).getFirst();
        for(var vertex:quad.vertices())assertEquals(new BedrockGeometry.Vec(0,0,1),vertex.normal());
    }
    @Test void invalidTopologyAndIndicesAreRejectedBeforeRendering() {
        for(String polys:List.of("[[[0,0,0],[1,0,0]]]", "[[[0,0,0],[4,0,0],[2,0,0]]]",
                "[[[0,0,0],[1,-1,0],[2,0,0]]]", "[[[0,0,0],[1,0,.5],[2,0,0]]]",
                "[[[0,0,0],[1,0,\"1\"],[2,0,0]]]", "\"tri_list\"", "\"bad_list\""))
            assertThrows(IllegalArgumentException.class,()->parse(TABLES+",\"polys\":"+polys));
    }
    @Test void aggregatePolygonBudgetIsEnforcedAcrossBones() {
        String face="[[0,0,0],[1,0,1],[2,0,2]]";
        String polygons="["+String.join(",",Collections.nCopies(32769,face))+"]";
        String bone="{\"name\":\"%s\",\"poly_mesh\":{"+TABLES+",\"polys\":"+polygons+"}}";
        assertThrows(IllegalArgumentException.class,()->BedrockGeometry.parse(JsonParser.parseString(
            "{\"bones\":["+bone.formatted("one")+","+bone.formatted("two")+"]}").getAsJsonObject()));
    }
}
