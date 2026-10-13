package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockEntityInstanceTest {
    @Test void attachablePhysicsQueriesUseTheCurrentHolderBinding() {
        var definition=BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"minecraft:test","geometry":{"default":"g"},
             "animations":{"read":"clip"},"scripts":{"animate":["read"]}}}
            """).getAsJsonObject());
        var geometry=BedrockGeometry.parse(JsonParser.parseString("{\"bones\":[{\"name\":\"item\",\"pivot\":[0,7.5,0]}]}").getAsJsonObject());
        var clip=new BedrockAnimation(JsonParser.parseString("""
            {"anim_time_update":"v.trs=q.bone_orientation_trs('item');return 0;",
             "bones":{"probe":{"position":["v.trs.t.x",0,0]}}}
            """).getAsJsonObject());
        var runtime=new BedrockEntityInstance(definition);
        for(int x:new int[]{4,12,20}) {
            var frame=runtime.withBindings(Map.of("item",new org.joml.Matrix4f().translation(x,20,0)),()->
                runtime.evaluateFrame((q,a)->0d,Map.of(),Map.of("clip",clip),Map.of(),OptionalInt.empty(),x,Map.of("g",geometry)));
            assertEquals(x,frame.poses().get("probe").position().x(),1e-5);
        }
    }
    @Test void nestedOverrideResetsPrecedingThisToBindPoseOnlyForReplacedBones() {
        var definition=BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"minecraft:test","geometry":{"default":"g"},
             "animations":{"base":"base","root":"root","child":"child","replace":"replace","follow":"follow"},
             "scripts":{"animate":["base","root"]}}}
            """).getAsJsonObject());
        var base=new BedrockAnimation(JsonParser.parseString("""
            {"bones":{"head":{"position":[5,0,0],"scale":3},"leg":{"position":[7,0,0]}}}
            """).getAsJsonObject());
        var replacement=new BedrockAnimation(JsonParser.parseString("""
            {"override_previous_animation":true,"bones":{"head":{"position":[2,0,0],"scale":2}}}
            """).getAsJsonObject());
        var follow=new BedrockAnimation(JsonParser.parseString("""
            {"bones":{"head":{"position":["this",0,0],"rotation":["this",0,0],"scale":"this"},
                      "leg":{"position":["this",0,0]}}}
            """).getAsJsonObject());
        var root=new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"animations":["child","follow"]}}}
            """).getAsJsonObject());
        var child=new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"animations":[{"replace":"q.replace_enabled"}]}}}
            """).getAsJsonObject());
        var geometry=BedrockGeometry.parse(JsonParser.parseString("""
            {"bones":[{"name":"head","rotation":[30,0,0]},{"name":"leg"}]}
            """).getAsJsonObject());
        for(boolean enabled:List.of(false,true)) {
            var frame=new BedrockEntityInstance(definition).evaluateFrame((q,a)->enabled?1d:0d,Map.of(),
                Map.of("base",base,"replace",replacement,"follow",follow),Map.of("root",root,"child",child),
                OptionalInt.empty(),0,Map.of("g",geometry));
            assertEquals(enabled?4:10,frame.poses().get("head").position().x(),"Only a weighted override may replace the outer clip before evaluating this");
            assertEquals(enabled?3:5,frame.poses().get("head").scale().x(),"Override must reset scale to bind scale, not the outer scale");
            assertEquals(30,frame.poses().get("head").rotation().x(),"this must still include geometry bind rotation");
            assertEquals(14,frame.poses().get("leg").position().x(),"An unrelated bone must retain the outer clip");
        }
    }

    @Test void animationClockIsScopedToClipAndTimelineNotEntityScripts() {
        var definition=BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"minecraft:test","animations":{"move":"clip"},
             "scripts":{"initialize":["v.start=q.anim_time;"],
              "pre_animation":["v.before=q.anim_time;"],"animate":["move"],
              "scalex":"1+v.before+q.anim_time","scaley":"v.start+v.event"}}}
            """).getAsJsonObject());
        var clip=new BedrockAnimation(JsonParser.parseString("""
            {"animation_length":2,"timeline":{"0.5":"v.event=q.anim_time;"},
             "bones":{"bone":{"position":["q.anim_time",0,0]}}}
            """).getAsJsonObject());
        var instance=new BedrockEntityInstance(definition);
        Molang.Queries facts=(q,a)->q.equals("anim_time")?99d:0d;
        instance.evaluateFrame(facts,Map.of(),Map.of("clip",clip),Map.of(),OptionalInt.empty(),0);
        var frame=instance.evaluateFrame(facts,Map.of(),Map.of("clip",clip),Map.of(),OptionalInt.empty(),.6);
        assertEquals(.6,frame.poses().get("bone").position().x(),1e-6);
        assertEquals(1,frame.scale().x());
        assertEquals(.5,frame.scale().y());
    }
    @Test void boneTrsIncludesInheritedModelSpacePivot() {
        var definition=BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"minecraft:test","geometry":{"default":"g"},
             "scripts":{"pre_animation":["v.anchor=q.bone_orientation_trs('child');v.rest=q.bone_rotation('parent');"],
                        "scalex":"v.anchor.t.x","scaley":"v.rest.z"}}}
            """).getAsJsonObject());
        var geometry=BedrockGeometry.parse(JsonParser.parseString("""
            {"bones":[{"name":"parent","rotation":[0,0,90]},
                      {"name":"child","parent":"parent","pivot":[0,10,0]}]}
            """).getAsJsonObject());
        var frame=new BedrockEntityInstance(definition).evaluateFrame((q,a)->0d,Map.of(),Map.of(),Map.of(),
            OptionalInt.empty(),0,Map.of("g",geometry));
        assertEquals(10,frame.scale().x(),1e-5);assertEquals(90,frame.scale().y(),1e-5);
    }
    @Test void zeroWeightOverrideCannotEraseActivePoseOrPlayUnrelatedSound() {
        var definition=BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"minecraft:test","animations":{"active":"a","muted":"b"},
             "sound_effects":{"wrong":"wrong_entity"},"scripts":{"animate":["active",{"muted":"0"}]}}}
            """).getAsJsonObject());
        var active=new BedrockAnimation(JsonParser.parseString("{\"bones\":{\"head\":{\"position\":[0,5,0]}}}").getAsJsonObject());
        var muted=new BedrockAnimation(JsonParser.parseString("""
            {"override_previous_animation":true,"anim_time_update":"v.wrong=99;return q.anim_time;","bones":{"head":{"position":[0,99,0]}},
             "sound_effects":{"0":{"effect":"wrong"}}}
            """).getAsJsonObject());
        var instance=new BedrockEntityInstance(definition);
        var frame=instance.evaluateFrame((q,a)->0d,Map.of(),
            Map.of("a",active,"b",muted),Map.of(),OptionalInt.empty(),0);
        assertEquals(5,frame.poses().get("head").position().y());assertTrue(frame.effects().isEmpty());
        assertEquals(0d,Molang.number(instance.withContext((q,a)->0d,c->c.variable("wrong"))));
    }
    @Test void modelScaleUsesPreAnimationVariablesAndAxisOverrides() {
        var definition=BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"minecraft:test","scripts":{"pre_animation":["v.size=2;"],"scale":"v.size","scaley":"3"}}}
            """).getAsJsonObject());
        var frame=new BedrockEntityInstance(definition).evaluateFrame((q,a)->0d,Map.of(),Map.of(),Map.of(),OptionalInt.empty(),0);
        assertEquals(new BedrockAnimation.Vector(2,3,2),frame.scale());
    }
    @Test void nestedControllerClocksRestartOnParentStateEntry() {
        var definition=BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"minecraft:test","animations":{
             "root":"controller.animation.root","child":"controller.animation.child","clip":"animation.clip"},
             "scripts":{"animate":["root"]}}}
            """).getAsJsonObject());
        var root=new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"animations":["child"],"transitions":[{"next":"q.state_time >= 1"}]},
             "next":{"animations":["child"]}}}
            """).getAsJsonObject());
        var child=new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"animations":["clip"]}}}
            """).getAsJsonObject());
        var clip=new BedrockAnimation(JsonParser.parseString("""
            {"bones":{"bone":{"position":["q.anim_time",0,0]}}}
            """).getAsJsonObject());
        var instance=new BedrockEntityInstance(definition);
        var controllers=Map.of("controller.animation.root",root,"controller.animation.child",child);
        assertEquals(0,instance.evaluateFrame((q,a)->0d,Map.of(),Map.of("animation.clip",clip),controllers,OptionalInt.empty(),0).poses().get("bone").position().x());
        assertEquals(.5,instance.evaluateFrame((q,a)->0d,Map.of(),Map.of("animation.clip",clip),controllers,OptionalInt.empty(),.5).poses().get("bone").position().x());
        assertEquals(0,instance.evaluateFrame((q,a)->0d,Map.of(),Map.of("animation.clip",clip),controllers,OptionalInt.empty(),1).poses().get("bone").position().x());
        assertEquals(.5,instance.evaluateFrame((q,a)->0d,Map.of(),Map.of("animation.clip",clip),controllers,OptionalInt.empty(),1.5).poses().get("bone").position().x());
    }
    @Test void entityAnimationAliasesDriveNativeMesh() {
        var definition=BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"minecraft:test","geometry":{"default":"geometry.test"},
            "textures":{"default":"textures/test"},"animations":{"move":"animation.test"},
            "scripts":{"animate":["move"]},"render_controllers":["controller.render.test"]}}
            """).getAsJsonObject());
        var controller=new BedrockRenderController(JsonParser.parseString("""
            {"geometry":"Geometry.default","textures":["Texture.default"]}
            """).getAsJsonObject());
        var clip=new BedrockAnimation(JsonParser.parseString("""
            {"loop":true,"animation_length":2,"bones":{"original_bone":{"position":{
             "0":[0,0,0],"2":[4,0,0]}}}}
            """).getAsJsonObject());
        var geometry=BedrockGeometry.parse(JsonParser.parseString("""
            {"bones":[{"name":"original_bone","cubes":[{"size":[1,1,1]}]}]}
            """).getAsJsonObject());
        var instance=new BedrockEntityInstance(definition);
        var frame=instance.evaluateFrame((q,a)->0d,Map.of("controller.render.test",controller),
            Map.of("animation.test",clip),Map.of(),OptionalInt.empty(),1);
        assertEquals("geometry.test",frame.selections().getFirst().geometry());
        assertEquals(2,frame.poses().get("original_bone").position().x());
        var mesh=BedrockGeometryMesh.bake(geometry,frame.poses(),frame.selections().getFirst().visibility());
        assertEquals(6,mesh.size());
        assertTrue(mesh.stream().flatMap(q->q.vertices().stream()).allMatch(v->v.x()>=2 && v.x()<=3));
    }
    @Test void scriptsArePerEntityAndSynchronizedPoseOverridesInitializeDefault() {
        var definition = BedrockClientEntity.parse(JsonParser.parseString("""
            {"description":{"identifier":"minecraft:armor_stand",
             "geometry":{"default":"geometry.zero","posed":"geometry.posed"},
             "textures":{"default":"textures/stand"},
             "scripts":{"initialize":["v.armor_stand.pose_index=0;v.counter=0;"],
                        "pre_animation":["v.counter=v.counter+1;"]},
             "render_controllers":["controller.render.stand"]}}
            """).getAsJsonObject());
        var controller = new BedrockRenderController(JsonParser.parseString("""
            {"geometry":"v.armor_stand.pose_index > 0 || v.counter > 1 ? Geometry.posed : Geometry.default",
             "textures":["Texture.default"]}
            """).getAsJsonObject());
        var controllers = Map.of("controller.render.stand", controller);
        var first = new BedrockEntityInstance(definition);
        var second = new BedrockEntityInstance(definition);
        Molang.Queries queries = (name,args) -> {throw new IllegalArgumentException(name);};
        assertEquals("geometry.posed", first.evaluate(queries,controllers,OptionalInt.of(2)).getFirst().geometry());
        assertEquals("geometry.zero", second.evaluate(queries,controllers,OptionalInt.empty()).getFirst().geometry());
        assertEquals("geometry.posed", second.evaluate(queries,controllers,OptionalInt.empty()).getFirst().geometry());
        var reloaded = new BedrockEntityInstance(definition);
        assertEquals("geometry.zero", reloaded.evaluate(queries,controllers,OptionalInt.empty()).getFirst().geometry());
    }
}
