package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class ReflectedCubeTest {
    @Test void magmaCorrectionOnlySwapsPairedTranslucentShell() {
        var geometry=BedrockGeometry.parse(JsonParser.parseString("""
            {"description":{"texture_width":16,"texture_height":16},"bones":[
             {"name":"shell","cubes":[{"origin":[1,1,1],"size":[-2,-2,-2],"uv":[0,0]},
              {"origin":[-1,-1,-1],"size":[2,2,2],"uv":[0,0]}]},
             {"name":"face","cubes":[{"origin":[-1,-1,-1.1],"size":[2,2,0],"uv":[0,0]}]}]}
            """).getAsJsonObject());
        var original=BedrockGeometryMesh.bake(geometry,Map.of(),List.of());
        var translucent=new BedrockMaterials.Material(true,false,false,true,List.of());
        var corrected=SulfurCubeGeometry.magmaShell(geometry,original,translucent);
        for(int i=0;i<original.size();i++) {
            if(original.get(i).bone().equals("face"))assertSame(original.get(i),corrected.get(i));
            else assertEquals(original.get(i).vertices().getLast(),corrected.get(i).vertices().getFirst());
        }
        assertEquals(original,SulfurCubeGeometry.magmaShell(geometry,original,
            new BedrockMaterials.Material(false,false,false,true,List.of())));
    }
    @Test void sulfurCoreCorrectionLeavesTranslucencyAndOrdinaryCubesUntouched() {
        var geometry=BedrockGeometry.parse(JsonParser.parseString("""
            {"description":{"texture_width":16,"texture_height":16},"bones":[
             {"name":"core","cubes":[{"origin":[1,1,1],"size":[-2,-2,-2],"uv":[0,0]}]},
             {"name":"outer","cubes":[{"origin":[-1,-1,-1],"size":[2,2,2],"uv":[0,0]}]}]}
            """).getAsJsonObject());
        var original=BedrockGeometryMesh.bake(geometry,Map.of(),List.of());
        var opaque=new BedrockMaterials.Material(false,false,false,true,List.of());
        var corrected=SulfurCubeGeometry.opaqueCore(geometry,original,opaque);
        for(int i=0;i<original.size();i++) {
            var q=corrected.get(i);
            if(q.bone().equals("outer"))assertSame(original.get(i),q);
            else {
                var center=new Vector3f();for(var v:q.vertices())center.add(v.x(),v.y(),v.z());center.div(4);
                assertTrue(center.dot(new Vector3f(q.nx(),q.ny(),q.nz()))>0);
                assertEquals(original.get(i).vertices().getLast(),q.vertices().getFirst());
            }
        }
        assertEquals(original,SulfurCubeGeometry.opaqueCore(geometry,original,
            new BedrockMaterials.Material(true,false,false,true,List.of())));
        assertEquals(original,SulfurCubeGeometry.opaqueCore(geometry,original,
            new BedrockMaterials.Material(false,false,false,false,List.of())));
    }
    @Test void containedJavaBlockKeepsFrontFacesAndBottomCenter() {
        var anchor=new org.joml.Matrix4f().translate(0,1,0);
        var transform=BedrockModelSpace.containedBlockTransform(anchor,1);
        assertTrue(transform.determinant3x3()>0,"Java block vertices must not inherit an uncorrected reflection");
        var expected=BedrockModelSpace.rendererTransform().mul(anchor).transformPosition(new Vector3f());
        var bottom=transform.transformPosition(new Vector3f(.5f,0,.5f));
        assertEquals(expected.x,bottom.x,1e-6);
        assertEquals(expected.y,bottom.y,1e-6);
        assertEquals(expected.z,bottom.z,1e-6);
    }
    @Test void negativeBoneScalePreservesOutwardFacesAndNormals() {
        var geometry=BedrockGeometry.parse(JsonParser.parseString("""
            {"description":{"texture_width":16,"texture_height":16},
             "bones":[{"name":"cube","cubes":[{"origin":[-1,-1,-1],"size":[2,2,2],"uv":[0,0]}]}]}
            """).getAsJsonObject());
        for(int sign:List.of(1,-1)) {
            var pose=new BedrockAnimation.Pose(new BedrockAnimation.Vector(0,0,0),new BedrockAnimation.Vector(0,0,0),new BedrockAnimation.Vector(sign,1,1));
            for(var q:BedrockGeometryMesh.bake(geometry,Map.of("cube",pose),List.of())) {
                var center=new Vector3f();for(var v:q.vertices())center.add(v.x(),v.y(),v.z());center.div(4);
                var a=q.vertices().get(0);var b=q.vertices().get(1);var c=q.vertices().get(2);
                var normal=new Vector3f(b.x()-a.x(),b.y()-a.y(),b.z()-a.z()).cross(new Vector3f(c.x()-a.x(),c.y()-a.y(),c.z()-a.z())).normalize();
                assertTrue(normal.dot(center)>0,"Reflected shell must not expose its back faces");
                assertTrue(normal.dot(new Vector3f(q.nx(),q.ny(),q.nz()))>.99);
                var points=new ArrayList<Vector3f>();
                for(int i=0;i<q.vertices().size();i++) {
                    var v=q.vertices().get(BedrockModelSpace.rendererVertexIndex(i,q.vertices().size()));
                    points.add(BedrockModelSpace.rendererTransform().transformPosition(new Vector3f(v.x(),v.y(),v.z())));
                }
                var renderedNormal=new Vector3f(points.get(1)).sub(points.get(0))
                    .cross(new Vector3f(points.get(2)).sub(points.get(0))).normalize();
                assertTrue(renderedNormal.dot(new Vector3f(q.nx(),-q.ny(),q.nz()))>.99,
                    "Renderer Y conversion must preserve front faces for culled models");
            }
        }
    }
}
