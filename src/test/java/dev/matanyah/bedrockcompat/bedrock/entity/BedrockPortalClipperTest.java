package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockPortalClipperTest {
    @Test void glowShellExcludesBodySilhouetteButKeepsItsOutline() {
        var defs=Map.of("body",JsonParser.parseString("""
            {"+states":["EnableStencilTest","StencilWrite"],"stencilRef":2,
             "frontFace":{"stencilFunc":"Always","stencilPassOp":"Replace"}}
            """).getAsJsonObject(),"glow",JsonParser.parseString("""
            {"+states":["EnableStencilTest"],"stencilRef":2,"frontFace":{"stencilFunc":"NotEqual"}}
            """).getAsJsonObject());
        var materials=new BedrockMaterials(defs);
        var surfaces=List.of(new BedrockPortalClipper.Surface(materials.resolve("body"),List.of(quad(-1,-1,0,2))),
            new BedrockPortalClipper.Surface(materials.resolve("glow"),List.of(quad(-2,-2,0,4))));
        var result=BedrockPortalClipper.clip(surfaces,new Vector3f(0,0,-4));
        assertFalse(result.get(1).isEmpty());
        double area=0;
        for(var q:result.get(1)) {
            var a=q.vertices().get(0);var b=q.vertices().get(1);var c=q.vertices().get(2);
            area+=Math.abs((b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x()))/2;
        }
        assertEquals(12,area,1e-5);
        var repeated=new ArrayList<BedrockGeometryMesh.Quad>(Collections.nCopies(200,quad(-1,-1,0,2)));
        repeated.add(quad(0,0,0,0));
        var repeatedResult=BedrockPortalClipper.clip(List.of(
            new BedrockPortalClipper.Surface(materials.resolve("body"),repeated),surfaces.get(1)),new Vector3f(0,0,-4));
        assertTrue(repeatedResult.get(1).size()<32,"Repeated/boundary masks must not multiply degenerate fragments");
    }
    private static BedrockGeometryMesh.Quad quad(float x,float y,float z,float size) {
        return new BedrockGeometryMesh.Quad("arbitrary","*",List.of(
            new BedrockGeometryMesh.Vertex(x,y,z,0,0),new BedrockGeometryMesh.Vertex(x+size,y,z,1,0),
            new BedrockGeometryMesh.Vertex(x+size,y+size,z,1,1),new BedrockGeometryMesh.Vertex(x,y+size,z,0,1)),0,0,1);
    }
    @Test void masksAreInheritedAndInteriorIsClippedToTheOpening() {
        var definitions=new HashMap<String,com.google.gson.JsonObject>();
        var json=JsonParser.parseString("""
            {"read":{"+states":["EnableStencilTest"],"stencilRef":95,"frontFace":{"stencilFunc":"Equal"}},
             "mask":{"bedrock_parent":"read","+states":["StencilWrite","DisableColorWrite"],
                     "stencilRef":94,"frontFace":{"stencilFunc":"Always","stencilPassOp":"Replace"}},
             "promote":{"bedrock_parent":"mask","stencilRef":95,"stencilReadMask":254,
                        "frontFace":{"stencilFunc":"Equal"}}}
            """).getAsJsonObject();json.entrySet().forEach(e->definitions.put(e.getKey(),e.getValue().getAsJsonObject()));
        var materials=new BedrockMaterials(definitions);
        var surfaces=List.of(new BedrockPortalClipper.Surface(materials.resolve("read"),List.of(quad(-10,-10,2,20))),
            new BedrockPortalClipper.Surface(materials.resolve("mask"),List.of(quad(-1,-1,0,2))),
            new BedrockPortalClipper.Surface(materials.resolve("promote"),List.of(quad(-1,-1,0,2))));
        var result=BedrockPortalClipper.clip(surfaces,new Vector3f(0,0,-2));
        assertFalse(result.getFirst().isEmpty());assertTrue(result.get(1).isEmpty());assertTrue(result.get(2).isEmpty());
        for(var q:result.getFirst())for(var v:q.vertices()) {
            assertTrue(Math.abs(v.x())<=2.001);assertTrue(Math.abs(v.y())<=2.001);
            assertTrue(Float.isFinite(v.u())&&Float.isFinite(v.v()));
        }
        assertTrue(BedrockPortalClipper.clip(List.of(surfaces.getFirst()),new Vector3f(0,0,-2)).getFirst().isEmpty());
        assertFalse(materials.resolve("promote").colorWrite());
        assertEquals(254,materials.resolve("promote").stencil().readMask());
    }
    @Test void cameraCoordinatesMatchEntityYawAndModelHandedness() {
        assertEquals(new Vector3f(16,32,-48),BedrockModelSpace.camera(1,2,3,0));
        var turned=BedrockModelSpace.camera(1,2,3,90);
        assertEquals(48,turned.x,1e-5);assertEquals(16,turned.z,1e-5);
        var rendered=BedrockModelSpace.rendererTransform().transformPosition(new Vector3f(16,24,0));
        assertEquals(new Vector3f(1,0,0),rendered);
        var cameraMesh=BedrockGeometryMesh.cameraFacing(List.of(quad(1,2,3,1)));
        assertEquals(-1,cameraMesh.getFirst().vertices().getFirst().x());
        assertEquals(-3,cameraMesh.getFirst().vertices().getFirst().z());
    }
    @Test void sleepingYawDoesNotApplyTheStandingHalfTurn() {
        for(float bedYaw:new float[]{0,90,180,270}) {
            assertEquals(-bedYaw,BedrockModelSpace.livingYaw(37,bedYaw));
            assertEquals(180-bedYaw,BedrockModelSpace.livingYaw(bedYaw,null));
        }
    }
}
