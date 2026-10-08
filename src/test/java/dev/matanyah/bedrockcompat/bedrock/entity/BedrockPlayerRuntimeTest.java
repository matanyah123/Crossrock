package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockPlayerRuntimeTest {
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
