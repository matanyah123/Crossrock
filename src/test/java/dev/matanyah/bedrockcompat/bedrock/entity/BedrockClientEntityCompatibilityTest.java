package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockClientEntityCompatibilityTest {
    private static BedrockClientEntity definition(String fields) {
        return BedrockClientEntity.parse(JsonParser.parseString("{\"description\":{\"identifier\":\"test:actor\","+fields+"}}").getAsJsonObject());
    }
    private static BedrockEntityInstance.Frame frame(BedrockEntityInstance runtime,double time) {
        return runtime.evaluateFrame((q,a)->0d,Map.of(),Map.of(),Map.of(),OptionalInt.empty(),time);
    }
    @Test void uppercaseAxisScalesExecuteAfterPreAnimationAndOverrideUniform() {
        var runtime=new BedrockEntityInstance(definition("""
            "scripts":{"scale":"v.size","scaleX":"v.size+1","scaleY":3,"scaleZ":4,
            "pre_animation":["v.size=2;"]}
            """));
        assertEquals(new BedrockAnimation.Vector(3,3,4),frame(runtime,0).scale());
    }
    @Test void defaultControlsPreserveArmorLightingAndHeldItems() {
        var entity=definition("\"enable_attachables\":true");
        assertFalse(entity.hideArmor());assertFalse(entity.heldItemIgnoresLighting());
        assertFalse(Molang.truth(entity.hideHeldItems().evaluate(new Molang.Context((q,a)->0d))));
    }
    @Test void authoredEquipmentControlsRemainIndependent() {
        var entity=definition("""
            "enable_attachables":true,"hide_armor":true,"held_item_ignores_lighting":true,
            "scripts":{"hide_held_items":"q.is_sleeping"}
            """ );
        assertTrue(entity.enableAttachables());assertTrue(entity.hideArmor());assertTrue(entity.heldItemIgnoresLighting());
        assertTrue(Molang.truth(entity.hideHeldItems().evaluate(new Molang.Context((q,a)->1d))));
    }
    @Test void legacyDeclarationsExecuteOrderedControllersWithoutScriptsAnimate() {
        var entity=definition("""
            "animation_controllers":[{"first":"controller.first"},{"second":"controller.second"}]
            """ );
        var a=new BedrockAnimationController(JsonParser.parseString("{\"states\":{\"default\":{\"on_entry\":[\"v.order=2;\"]}}}").getAsJsonObject());
        var b=new BedrockAnimationController(JsonParser.parseString("{\"states\":{\"default\":{\"on_entry\":[\"v.order=v.order*3;\"]}}}").getAsJsonObject());
        var runtime=new BedrockEntityInstance(entity);
        runtime.evaluateFrame((q,args)->0d,Map.of(),Map.of(),Map.of("controller.first",a,"controller.second",b),OptionalInt.empty(),0);
        assertEquals(6d,(Object)runtime.withContext((q,args)->0d,c->c.variable("order")));
    }
    @Test void duplicateLegacyAliasesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->definition("\"animations\":{\"a\":\"clip\"},\"animation_controllers\":[{\"a\":\"controller\"}]"));
    }
    @Test void legacyStringControllersUseDirectCrossFileIdentifiers() {
        var entity=definition("\"animation_controllers\":[\"controller.direct\"]");
        var controller=new BedrockAnimationController(JsonParser.parseString("{\"states\":{\"default\":{\"on_entry\":[\"v.ran=1;\"]}}}").getAsJsonObject());
        var runtime=new BedrockEntityInstance(entity);
        runtime.evaluateFrame((q,a)->0d,Map.of(),Map.of(),Map.of("controller.direct",controller),OptionalInt.empty(),0);
        assertEquals(1d,(Object)runtime.withContext((q,a)->0d,c->c.variable("ran")));
    }
    @Test void missingAnimationAliasDoesNotDiscardValidClips() {
        var messages=new ArrayList<String>();
        var runtime=new BedrockEntityInstance(definition("\"animations\":{\"ok\":\"clip\"},\"scripts\":{\"animate\":[\"missing\",\"ok\"]}"),0,messages::add);
        var clip=new BedrockAnimation(JsonParser.parseString("{\"bones\":{\"root\":{\"position\":[2,0,0]}}}").getAsJsonObject());
        var result=runtime.evaluateFrame((q,a)->0d,Map.of(),Map.of("clip",clip),Map.of(),OptionalInt.empty(),0);
        assertEquals(2,result.poses().get("root").position().x());assertEquals(1,messages.size());
    }
    @Test void publicSnapshotsExposeOnlyDeclaredVariablesAndDoNotRetainQueriesOrMutableScope() {
        var entity=definition("""
            "scripts":{"variables":{"variable.Visible":"public"},"initialize":["v.visible=7;v.secret=9;"]}
            """ );
        var runtime=new BedrockEntityInstance(entity);frame(runtime,0);
        var snapshot=runtime.publicContext();var reader=new Molang.Context((q,a)->0d);reader.setEntityContext("other",snapshot);
        assertEquals(7d,Molang.compile("c.other->v.visible").evaluate(reader));
        assertEquals(0d,Molang.compile("c.other->v.secret").evaluate(reader));
        snapshot.setVariable("visible",100d);
        assertEquals(7d,runtime.publicContext().variable("visible"));
        assertThrows(IllegalArgumentException.class,()->Molang.compile("q.life_time").evaluate(snapshot));
        var other=new BedrockEntityInstance(entity);assertEquals(0d,other.publicContext().variable("visible"));
    }
    @Test void legacyLocatorUsesModelCoordinatesAndAnimatedParentNotDoublePivot() {
        var entity=definition("\"locators\":{\"lead\":{\"head\":[0,14,-6]}}");
        var geometry=BedrockGeometry.parse(JsonParser.parseString("{\"bones\":[{\"name\":\"root\"},{\"name\":\"head\",\"parent\":\"root\",\"pivot\":[0,10,0]}]}").getAsJsonObject());
        var pose=new BedrockAnimation.Pose(new BedrockAnimation.Vector(5,0,0),new BedrockAnimation.Vector(0,0,0),new BedrockAnimation.Vector(1,1,1));
        var runtime=new BedrockEntityInstance(entity);
        var point=runtime.anchors(geometry,Map.of("root",pose)).get("lead").getTranslation(new org.joml.Vector3f());
        assertEquals(5,point.x);assertEquals(14,point.y);assertEquals(-6,point.z);
    }
    @Test void missingLocatorBoneIsIsolatedAndReported() {
        var errors=new ArrayList<String>();var runtime=new BedrockEntityInstance(definition("\"locators\":{\"lead\":{\"missing\":[0,1,2]}}"),0,errors::add);
        var geometry=BedrockGeometry.parse(JsonParser.parseString("{\"bones\":[{\"name\":\"root\"}]}").getAsJsonObject());
        assertFalse(runtime.anchors(geometry,Map.of()).containsKey("lead"));assertEquals(1,errors.size());
    }
    @Test void locatorQueriesFollowGeometrySelectionWithoutStaleAnchors() {
        var entity=definition("""
            "geometry":{"default":"g"},"locators":{"lead":{"head":[0,14,-6]}},
            "scripts":{"pre_animation":["v.y=q.get_root_locator_offset('lead',1);"]}
            """ );
        var runtime=new BedrockEntityInstance(entity);
        var geometry=BedrockGeometry.parse(JsonParser.parseString("{\"bones\":[{\"name\":\"head\",\"pivot\":[0,10,0]}]}").getAsJsonObject());
        runtime.evaluateFrame((q,a)->0d,Map.of(),Map.of(),Map.of(),OptionalInt.empty(),0,Map.of("g",geometry));
        assertEquals(14d,(Object)runtime.withContext((q,a)->0d,c->c.variable("y")));
        runtime.evaluateFrame((q,a)->0d,Map.of(),Map.of(),Map.of(),OptionalInt.empty(),1,Map.of());
        assertEquals(0d,(Object)runtime.withContext((q,a)->0d,c->c.variable("y")));
    }
    @Test void repeatedTimestampDoesNotRerunInitializeOrPreAnimation() {
        var runtime=new BedrockEntityInstance(definition("\"scripts\":{\"initialize\":[\"v.count=10;\"],\"pre_animation\":[\"v.count=v.count+1;\"]}"));
        frame(runtime,1);frame(runtime,1);frame(runtime,.5);
        assertEquals(11d,(Object)runtime.withContext((q,a)->0d,c->c.variable("count")));
        frame(runtime,2);assertEquals(12d,(Object)runtime.withContext((q,a)->0d,c->c.variable("count")));
    }
}
