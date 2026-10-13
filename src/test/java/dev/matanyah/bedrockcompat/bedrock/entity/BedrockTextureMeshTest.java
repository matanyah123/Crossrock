package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockTextureMeshTest {
    @Test void textureOffsetsUseDownwardYFromBonePivotAndCorrectExtrusionWinding() {
        var texture=new BedrockGeometry.TextureMesh("item",new BedrockGeometry.Vec(3,4,5),
            BedrockGeometry.Vec.ZERO,BedrockGeometry.Vec.ZERO,new BedrockGeometry.Vec(1,1,1),false);
        var faces=BedrockTextureMesh.polygons(texture,1,1,1,1,20,(x,y)->-1);
        assertEquals(15,faces.stream().flatMap(p->p.vertices().stream()).mapToDouble(v->v.position().y()).min().orElseThrow());
        assertEquals(16,faces.stream().flatMap(p->p.vertices().stream()).mapToDouble(v->v.position().y()).max().orElseThrow());
        for(var face:faces) {
            var a=face.vertices().get(0).position();var b=face.vertices().get(1).position();var c=face.vertices().get(2).position();
            var normal=new org.joml.Vector3d(b.x()-a.x(),b.y()-a.y(),b.z()-a.z())
                .cross(c.x()-a.x(),c.y()-a.y(),c.z()-a.z());
            var n=face.vertices().getFirst().normal();
            assertTrue(normal.dot(n.x(),n.y(),n.z())>0,"Texture voxel winding must match its outward normal");
        }
    }
    private static BedrockGeometry.TextureMesh mesh(boolean pixelDepth) {
        return new BedrockGeometry.TextureMesh("item",BedrockGeometry.Vec.ZERO,BedrockGeometry.Vec.ZERO,
            BedrockGeometry.Vec.ZERO,new BedrockGeometry.Vec(1,1,1),pixelDepth);
    }
    @Test void alphaHolesAndInternalWalls() {
        var faces=BedrockTextureMesh.polygons(mesh(false),2,2,16,16,(x,y)->y==0?0xffffffff:0);
        assertEquals(10,faces.size()); // Two adjacent voxels: shared faces are removed.
        for(var face:faces)for(var vertex:face.vertices()) {
            assertTrue(vertex.u()>0&&vertex.u()<1&&vertex.v()>0&&vertex.v()<1);
            assertEquals(.25,vertex.v());
        }
        assertTrue(BedrockTextureMesh.polygons(mesh(false),1,1,16,16,(x,y)->0).isEmpty());
    }
    @Test void pixelDepthAndTransforms() {
        var pixel=BedrockTextureMesh.polygons(mesh(true),2,2,16,16,(x,y)->x==0&&y==0?-1:0);
        assertEquals(-8,pixel.stream().flatMap(p->p.vertices().stream()).mapToDouble(v->v.position().y()).min().orElseThrow());
        var translated=new BedrockGeometry.TextureMesh("item",new BedrockGeometry.Vec(3,4,5),
            new BedrockGeometry.Vec(1,0,0),BedrockGeometry.Vec.ZERO,new BedrockGeometry.Vec(2,1,1),false);
        var faces=BedrockTextureMesh.polygons(translated,1,1,1,1,(x,y)->-1);
        assertEquals(1,faces.stream().flatMap(p->p.vertices().stream()).mapToDouble(v->v.position().x()).min().orElseThrow());
        assertEquals(3,faces.stream().flatMap(p->p.vertices().stream()).mapToDouble(v->v.position().x()).max().orElseThrow());
        assertThrows(IllegalArgumentException.class,()->BedrockTextureMesh.polygons(mesh(false),1000,1000,16,16,(x,y)->-1));
    }
    @Test void parserAndLayerPreserveHierarchyAndBindings() {
        var geometry=BedrockGeometry.parse(JsonParser.parseString("""
            {"description":{"texture_width":16,"texture_height":16},"bones":[
             {"name":"root"},{"name":"item","parent":"root","binding":"'rightitem'",
              "texture_meshes":[{"texture":"item","rotation":[-90,0,-45],"use_pixel_depth":true}]}]}
            """).getAsJsonObject());
        var bone=geometry.bones().get(1);
        assertEquals(1,bone.textureMeshes().size());
        assertTrue(bone.textureMeshes().getFirst().pixelDepth());
        var polygons=BedrockTextureMesh.polygons(mesh(false),1,1,16,16,(x,y)->-1);
        var layer=BedrockTextureMesh.layer(geometry,"item",polygons);
        assertEquals("root",layer.bones().get(1).parent());
        assertSame(bone.binding(),layer.bones().get(1).binding());
        assertEquals(6,layer.bones().get(1).polygons().size());
        assertTrue(layer.bones().get(1).textureMeshes().isEmpty());
    }
}
