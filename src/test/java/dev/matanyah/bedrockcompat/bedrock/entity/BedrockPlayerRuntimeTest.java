package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockPlayerRuntimeTest {
    @Test void engineElytraHasBothWingsAndPackAnimationsOverrideDefaults() {
        var geometries=new HashMap<String,BedrockGeometry>();BedrockPlayerDefaults.geometries(geometries);
        var clips=new HashMap<String,BedrockAnimation>();BedrockPlayerDefaults.animations(clips);
        var geometry=geometries.get("geometry.elytra");
        assertEquals(12,BedrockGeometryMesh.bake(geometry,Map.of(),List.of()).size());
        var pose=clips.get("animation.elytra.gliding").sample(0,new Molang.Context((q,a)->0d),Map.of());
        assertEquals(-90,pose.get("left_wing").rotation().z());
        assertEquals(90,pose.get("right_wing").rotation().z());
        var custom=clips.get("animation.elytra.sneaking");
        clips.put("animation.elytra.gliding",custom);BedrockPlayerDefaults.animations(clips);
        assertSame(custom,clips.get("animation.elytra.gliding"));
        assertSame(geometry,geometries.get("geometry.elytra"));
    }
    @Test void missingVanillaSleepClipLiesDownAndPackOverrideWins() {
        var clips=new HashMap<String,BedrockAnimation>();BedrockPlayerDefaults.animations(clips);
        var sleep=clips.get("animation.player.sleeping");
        var pose=sleep.sample(0,new Molang.Context((name,args)->0d),Map.of());
        assertEquals(-90,pose.get("root").rotation().x());
        assertTrue(sleep.overridesPrevious());
        var custom=new BedrockAnimation(JsonParser.parseString("{\"bones\":{\"root\":{\"rotation\":[45,0,0]}}}").getAsJsonObject());
        clips.put("animation.player.sleeping",custom);BedrockPlayerDefaults.animations(clips);
        assertSame(custom,clips.get("animation.player.sleeping"));
    }
    @Test void facialHelpersReadTheirOwnLocalBindPositionIncludingAdditionalLayers() {
        var entity=BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"minecraft:player","geometry":{"default":"body","face":"face"},
             "animations":{"move":"move","read":"read","apply":"apply"},
             "scripts":{"animate":["move","read","apply"]}}}
            """).getAsJsonObject());
        var body=BedrockGeometry.parse(JsonParser.parseString("""
            {"bones":[{"name":"head","pivot":[0,24,0]}]}
            """).getAsJsonObject());
        var face=BedrockGeometry.parse(JsonParser.parseString("""
            {"bones":[{"name":"head","pivot":[0,24,0]},
              {"name":"face","parent":"head","pivot":[0,28,-4]},
              {"name":"helper","parent":"face","pivot":[0,27,-4]},
              {"name":"left_helper","parent":"helper","pivot":[-2,27,-4]},
              {"name":"right_helper","parent":"helper","pivot":[2,27,-4]},
              {"name":"mouth_helper","parent":"face","pivot":[0,28.5,-4]},
              {"name":"left_eye","parent":"face","pivot":[-2,27,-4],
               "cubes":[{"origin":[-2.5,26.5,-4],"size":[1,1,0],"uv":[0,0]}]},
              {"name":"right_eye","parent":"face","pivot":[2,27,-4]},
              {"name":"mouth","parent":"face","pivot":[0,26,-4]}]}
            """).getAsJsonObject());
        var move=new BedrockAnimation(JsonParser.parseString("""
            {"loop":true,"bones":{"left_helper":{"position":["q.eye_offset",0,0]}}}
            """).getAsJsonObject());
        var read=new BedrockAnimation(JsonParser.parseString("""
            {"loop":true,"bones":{
              "left_helper":{"position":["v.left=this+2;",0,0]},
              "right_helper":{"position":["v.right=this-2;",0,0]},
              "mouth_helper":{"position":[0,"v.mouth=this-0.5;",0]}}}
            """).getAsJsonObject());
        var apply=new BedrockAnimation(JsonParser.parseString("""
            {"loop":true,"bones":{"left_eye":{"position":["v.left",0,0]},
              "right_eye":{"position":["v.right",0,0]},"mouth":{"position":[0,"v.mouth",0]}}}
            """).getAsJsonObject());
        var instance=new BedrockEntityInstance(entity);
        for(double time:List.of(0d,.1,4d)) {
            double offset=time<1?0:-.25;
            var frame=instance.evaluateFrame((q,a)->q.equals("eye_offset")?offset:0d,Map.of(),Map.of("move",move,"read",read,"apply",apply),
                Map.of(),OptionalInt.empty(),time,Map.of("body",body,"face",face));
            assertEquals(offset,frame.poses().get("left_eye").position().x());
            assertEquals(0,frame.poses().get("right_eye").position().x());
            assertEquals(0,frame.poses().get("mouth").position().y());
            var eye=BedrockGeometryMesh.bake(face,frame.poses(),List.of()).getFirst();
            assertTrue(eye.vertices().stream().allMatch(v->v.x()>=-2.5+offset&&v.x()<=-1.5+offset),
                "A bind origin must inform this, not translate the rendered eye a second time");
        }
    }
    @Test void headYawIncludesBodyYawForWorldMovementAndInventoryMouseTracking() {
        assertEquals(210,BedrockModelSpace.headYaw(180,30));
        assertEquals(-100,BedrockModelSpace.headYaw(-90,-10));
        assertEquals(0,BedrockModelSpace.headYaw(350,10)%360);
    }
    @Test void cameraDepthReflectionPreservesUvAndOutwardWinding() {
        var original=BedrockGeometryMesh.bake(BedrockPlayerDefaults.humanoid(false),Map.of(),List.of());
        var reflected=BedrockGeometryMesh.reflectDepth(original);
        assertEquals(original.size(),reflected.size());
        for(int i=0;i<original.size();i++) {
            var a=original.get(i);var b=reflected.get(i);
            assertEquals(-a.nz(),b.nz());assertEquals(a.nx(),b.nx());
            for(int j=0;j<a.vertices().size();j++) {
                var before=a.vertices().get(a.vertices().size()-1-j);var after=b.vertices().get(j);
                assertEquals(before.u(),after.u());assertEquals(before.v(),after.v());assertEquals(-before.z(),after.z());
            }
        }
        assertEquals(original,BedrockGeometryMesh.reflectDepth(reflected));
    }
    @Test void removingInputProviderRestoresPackVariablesInsteadOfLeavingMorphActive() {
        var definition=BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"minecraft:player","scripts":{"scale":"v.form+1"}}}
            """).getAsJsonObject());
        var instance=new BedrockEntityInstance(definition);
        var active=instance.evaluateFrame((q,a)->0d,Map.of(),Map.of(),Map.of(),OptionalInt.empty(),0,Map.of(),Map.of("form",2d),Map.of());
        assertEquals(3,active.scale().x());
        var removed=instance.evaluateFrame((q,a)->0d,Map.of(),Map.of(),Map.of(),OptionalInt.empty(),.1,Map.of(),Map.of(),Map.of());
        assertEquals(1,removed.scale().x());
    }
    @Test void skinBindingsMorphsAndCameraInputsAreDataDriven() {
        var entity=BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"minecraft:player","geometry":{"default":"geometry.humanoid.custom","other":"geometry.other"},
             "textures":{"default":"textures/entity/steve"},"scripts":{"initialize":["v.is_first_person=99;"],
             "scale":"v.is_first_person?2:1","animate":["arm"]},"animations":{"arm":"animation.arm"},
             "render_controllers":["controller.render.test"]}}
            """).getAsJsonObject());
        var render=new BedrockRenderController(JsonParser.parseString("""
            {"geometry":"q.is_name_any('shape')?Geometry.other:Geometry.default","textures":["Texture.default"]}
            """).getAsJsonObject());
        var animation=new BedrockAnimation(JsonParser.parseString("""
            {"loop":true,"animation_length":2,"bones":{"rightArm":{"rotation":["v.attack_time*90",0,0]}}}
            """).getAsJsonObject());
        Map<String,BedrockRenderController> controllers=Map.of("controller.render.test",render);
        Map<String,BedrockAnimation> clips=Map.of("animation.arm",animation);
        var world=new BedrockEntityInstance(entity);var hands=new BedrockEntityInstance(entity);
        var query=new EntityQueryContext("shape","minecraft:plains",Set.of(),Map.of("hurt_time",0d,"is_on_fire",0d));
        var skin=Map.<String,Object>of("texture.default","runtime:skin");
        var a=world.evaluateFrame(query,controllers,clips,Map.of(),OptionalInt.empty(),0,Map.of(),
            Map.of("is_first_person",0d,"attack_time",.5d),skin);
        assertEquals("geometry.other",a.selections().getFirst().geometry());
        assertEquals("runtime:skin",a.selections().getFirst().textures().getFirst());
        assertEquals(45,a.poses().get("rightArm").rotation().x());assertEquals(1,a.scale().x());
        var b=hands.evaluateFrame(query,controllers,clips,Map.of(),OptionalInt.empty(),0,Map.of(),
            Map.of("is_first_person",1d,"attack_time",1d),skin);
        assertEquals(2,b.scale().x());assertEquals(90,b.poses().get("rightArm").rotation().x());
        var c=world.evaluateFrame(query,controllers,clips,Map.of(),OptionalInt.empty(),.5,Map.of(),
            Map.of("is_first_person",0d,"attack_time",0d),skin);
        assertEquals(0,c.poses().get("rightArm").rotation().x());assertEquals(1,c.scale().x());
    }
    @Test void fallbackMeshesAndControllersDoNotOverwritePackAssets() {
        var geometry=BedrockPlayerDefaults.humanoid(false);
        Map<String,BedrockGeometry> meshes=new HashMap<>();meshes.put("geometry.humanoid.custom",geometry);
        BedrockPlayerDefaults.geometries(meshes);assertSame(geometry,meshes.get("geometry.humanoid.custom"));
        assertEquals(64,meshes.get("bedrock_compat:humanoid_slim").textureWidth());
        assertTrue(BedrockGeometryMesh.anchors(geometry,Map.of()).containsKey("rightitem"));
        Map<String,BedrockRenderController> controllers=new HashMap<>();BedrockPlayerDefaults.controllers(controllers);
        var aliases=Map.<String,Object>of("geometry.default","g","material.default","entity_alphatest","texture.default","skin");
        var selected=controllers.get("controller.render.player.first_person").select(new Molang.Context((q,a)->0d),aliases);
        var arms=BedrockGeometryMesh.bake(geometry,Map.of(),selected.visibility());
        assertFalse(arms.isEmpty());assertTrue(arms.stream().allMatch(q->q.bone().contains("arm")||q.bone().contains("sleeve")));
    }
    @Test void movementDirectionUsesNormalizedVectorComponentsAndPackTogglesAreBoolean() {
        var query=new EntityQueryContext("","",Set.of(),Map.of("movement_direction",List.of(.6,0d,.8),
            "pack_settings",Map.of("enabled","true","disabled","false")));
        assertEquals(.8,query.get("movement_direction",List.of(2d)));
        assertEquals(1d,query.get("is_pack_setting_enabled",List.of("enabled")));
        assertEquals(0d,query.get("is_pack_setting_enabled",List.of("disabled")));
    }
}
