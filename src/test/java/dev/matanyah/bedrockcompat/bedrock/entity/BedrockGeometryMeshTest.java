package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockGeometryMeshTest {
    @Test void bedrockNegativePitchRaisesArmTowardTheFace() {
        var geometry=geometry("[{\"name\":\"arm\",\"pivot\":[0,20,0],\"rotation\":[-90,0,0],\"locators\":{\"hand\":[0,10,0]}}]");
        var hand=BedrockGeometryMesh.anchors(geometry,Map.of()).get("hand").getTranslation(new org.joml.Vector3f());
        assertEquals(20,hand.y,1e-5);assertEquals(-10,hand.z,1e-5);
    }
    @Test void entityRelativeRotationPreservesInheritedPivotButNotParentRotation() {
        var geometry=geometry("""
            [{"name":"parent","rotation":[0,0,90]},
             {"name":"child","parent":"parent","pivot":[0,10,0],"locators":{"tip":[0,11,0]}}]
            """);
        var pose=new BedrockAnimation.Pose(new BedrockAnimation.Vector(0,0,0),
            new BedrockAnimation.Vector(0,0,0),new BedrockAnimation.Vector(1,1,1),true);
        var anchors=BedrockGeometryMesh.anchors(geometry,Map.of("child",pose));
        var pivot=anchors.get("child").getTranslation(new org.joml.Vector3f());
        var tip=anchors.get("tip").getTranslation(new org.joml.Vector3f());
        assertEquals(10,pivot.x,1e-5);assertEquals(0,pivot.y,1e-5);
        assertEquals(10,tip.x,1e-5);assertEquals(1,tip.y,1e-5);
    }
    @Test void restAndAnimationEulerChannelsAreAddedBeforeRotation() {
        var geometry=geometry("[{\"name\":\"root\",\"rotation\":[30,50,20]}]");
        var pose=new BedrockAnimation.Pose(new BedrockAnimation.Vector(0,0,0),
            new BedrockAnimation.Vector(-30,-50,-20),new BedrockAnimation.Vector(1,1,1));
        var anchor=BedrockGeometryMesh.anchors(geometry,Map.of("root",pose)).get("root");
        assertTrue(anchor.equals(new org.joml.Matrix4f(),1e-5f),"Negated rest channels must cancel the bind rotation");
    }
    private BedrockGeometry geometry(String bones) {
        return BedrockGeometry.parse(JsonParser.parseString("{\"bones\":"+bones+"}").getAsJsonObject());
    }
    @Test void hierarchyTransformsCubeAroundParentPivot() {
        var geometry = geometry("""
            [{"name":"child","parent":"parent","cubes":[{"origin":[1,0,0],"size":[1,1,1],
             "uv":{"north":{"uv":[0,0]}}}]},
             {"name":"parent","rotation":[0,0,90]}]
            """);
        var quad = BedrockGeometryMesh.bake(geometry,Map.of(),List.of()).getFirst();
        assertEquals(-1,quad.nz(),1e-5);
        for(var vertex:quad.vertices()) {
            assertTrue(vertex.x() >= -0.0001 && vertex.x() <= 1.0001);
            assertTrue(vertex.y() >= -2.0001 && vertex.y() <= -0.9999);
        }
    }
    @Test void unitCubeHasOutwardNormalsAndVisibilityDoesNotHideChildrenImplicitly() {
        var geometry = geometry("""
            [{"name":"parent","cubes":[{"size":[1,1,1]}]},
             {"name":"child","parent":"parent","cubes":[{"size":[1,1,1]}]}]
            """);
        var mesh = BedrockGeometryMesh.bake(geometry,Map.of(),List.of(new BedrockRenderController.Visibility("parent",false)));
        assertEquals(6,mesh.size());
        assertTrue(mesh.stream().allMatch(q->q.bone().equals("child")));
        for(var quad:mesh) {
            double x=0,y=0,z=0;
            for(var v:quad.vertices()){x+=v.x()/4;y+=v.y()/4;z+=v.z()/4;}
            assertTrue((x-.5)*quad.nx()+(y-.5)*quad.ny()+(z-.5)*quad.nz()>0);
        }
    }
    @Test void boneAnimationAppliesToDescendantsWithoutMutatingGeometry() {
        var geometry = geometry("""
            [{"name":"p"},{"name":"c","parent":"p","cubes":[{"size":[1,1,1]}]}]
            """);
        var pose = new BedrockAnimation.Pose(new BedrockAnimation.Vector(4,0,0),
            new BedrockAnimation.Vector(0,0,0),new BedrockAnimation.Vector(1,1,1));
        var animated = BedrockGeometryMesh.bake(geometry,Map.of("p",pose),List.of());
        assertTrue(animated.stream().flatMap(q->q.vertices().stream()).allMatch(v->v.x()>=4));
        var original = BedrockGeometryMesh.bake(geometry,Map.of(),List.of());
        assertTrue(original.stream().flatMap(q->q.vertices().stream()).allMatch(v->v.x()<=1));
    }
    @Test void wildcardPatternsAreLiteralExceptForStars() {
        assertTrue(BedrockGeometryMesh.matches("arm*","arm_left"));
        assertTrue(BedrockGeometryMesh.matches("*hand*","left_hand_prop"));
        assertFalse(BedrockGeometryMesh.matches("arm.*","arm_left"));
    }
}
