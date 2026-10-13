package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import org.joml.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.lang.Math;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the production emitter and numerical motion contracts; no parser-only support credit. */
class BedrockParticleRuntimeTest {
    @Test void materialsSeparateAlphaTestFromBlendingAndAdditive() {
        assertEquals(BedrockParticleMotion.Material.CUTOUT,BedrockParticleMotion.material("particles_alpha"));
        assertEquals(BedrockParticleMotion.Material.BLEND,BedrockParticleMotion.material("particles_blend"));
        assertEquals(BedrockParticleMotion.Material.ADDITIVE,BedrockParticleMotion.material("particles_add"));
    }
    static BedrockParticleEffect effect(String components,String events) {
        return new BedrockParticleEffect(JsonParser.parseString("""
            {"description":{"identifier":"fixture:runtime","basic_render_parameters":{"material":"particles_alpha","texture":"textures/particle/fixture"}},
            "components":{%s},"events":{%s}}
            """.formatted(components,events)).getAsJsonObject());
    }
    static class Emitter extends BedrockParticleEmitter {
        final Matrix4f frame=new Matrix4f();final List<String> calls=new ArrayList<>();
        Vector3f inherited;Molang.Context childContext;Vector3f soundPosition;
        Emitter(BedrockParticleEffect d){this(d,Map.of(),"");}
        Emitter(BedrockParticleEffect d,Map<String,Object> variables,String script){super(d,(q,a)->0d,variables,script,123);}
        public Matrix4f transform(){return frame;}
        protected void spawnParticle(Vector3f v,boolean inherit){count++;calls.add("spawn");}
        protected void child(String id,String type,String script,Matrix4f at,Vector3f velocity) {
            calls.add("child:"+type);inherited=new Vector3f(velocity);
            var child=new Emitter(definition,Map.of(),script);childContext=child.context;
        }
        protected void sound(String name,Vector3f at){calls.add("sound:"+name);soundPosition=at;}
        protected void log(String message,Vector3f at){calls.add("log:"+message);}
        void steps(int n){for(int i=0;i<n;i++)update(.05);}
    }
    @Test void onceDefaultsToTenSecondsAndIncludesEndpointOnlyOnce() {
        var e=new Emitter(effect("\"minecraft:emitter_lifetime_once\":{\"active_time\":0.1},\"minecraft:emitter_lifetime_events\":{\"creation_event\":\"start\",\"timeline\":{\"0.1\":\"end\",\"0\":\"zero\"},\"expiration_event\":\"stop\"}",
            "\"start\":{\"log\":\"start\"},\"end\":{\"log\":\"end\"},\"zero\":{\"log\":\"zero\"},\"stop\":{\"log\":\"stop\"}"));
        e.steps(4);e.expire();assertEquals(List.of("log:start","log:zero","log:end","log:stop"),e.calls);assertTrue(e.finished);
        assertEquals(10,new Emitter(effect("\"minecraft:emitter_lifetime_once\":{}","")).lifetime);
    }
    @Test void instantZeroLifetimeFiresBurstAndExpiresButKeepsLiveParticleCount() {
        var e=new Emitter(effect("\"minecraft:emitter_lifetime_once\":{\"active_time\":0},\"minecraft:emitter_rate_instant\":{\"num_particles\":3}",""));
        e.steps(2);assertEquals(3,e.count);assertTrue(e.finished);
    }
    @Test void loopingSleepsReevaluatesDurationsAndRandomsWithoutReinitializingEmitter() {
        var e=new Emitter(effect("\"minecraft:emitter_initialization\":{\"creation_expression\":\"v.created=v.created+1;\",\"per_update_expression\":\"v.updates=v.updates+1;\"},\"minecraft:emitter_lifetime_looping\":{\"active_time\":0.1,\"sleep_time\":0.1},\"minecraft:emitter_rate_instant\":{\"num_particles\":1}",""));
        Object random=e.context.variable("emitter_random_1");e.steps(3);assertEquals(1,e.count);assertEquals(random,e.context.variable("emitter_random_1"));
        e.steps(2);assertEquals(2,e.count);assertNotEquals(random,e.context.variable("emitter_random_1"));assertEquals(1d,e.context.variable("created"));assertEquals(5d,e.context.variable("updates"));
    }
    @Test void steadyFractionalCreditAndCapDoNotBurstWhenFull() {
        var e=new Emitter(effect("\"minecraft:emitter_rate_steady\":{\"spawn_rate\":10,\"max_particles\":2}",""));
        e.steps(10);assertEquals(2,e.count);e.count=0;e.steps(2);assertEquals(1,e.count);
    }
    @Test void expressionActivationPausesWithoutDestroyingEmitterAndExpirationIsIdempotent() {
        var e=new Emitter(effect("\"minecraft:emitter_lifetime_expression\":{\"activation_expression\":\"v.on\",\"expiration_expression\":\"v.end\"},\"minecraft:emitter_rate_instant\":{\"num_particles\":2}",""));
        e.steps(2);assertEquals(0,e.count);assertFalse(e.finished);e.context.setVariable("on",1d);e.update(.05);assertEquals(2,e.count);
        e.context.setVariable("end",1d);e.steps(2);assertTrue(e.finished);
    }
    @Test void manualRateEvaluatesItsCapPerEmissionAndNeverEmitsAutomatically() {
        var e=new Emitter(effect("\"minecraft:emitter_rate_manual\":{\"max_particles\":\"v.limit\"}",""));
        e.context.setVariable("limit",1d);assertTrue(e.canEmitManually());e.count=1;assertFalse(e.canEmitManually());
        e.context.setVariable("limit",2d);assertTrue(e.canEmitManually());e.steps(30);assertEquals(1,e.count);assertFalse(e.finished);
    }
    @Test void eventChainsUseFiringEmitterAndChildStartupIsIsolatedWithVelocity() {
        var e=new Emitter(effect("", """
            "chain":{"expression":"v.counter=v.counter+1;t.secret=99;","sequence":[
                {"sound_effect":{"event_name":"fixture.click"}},
                {"particle_effect":{"effect":"fixture:child","type":"particle_with_velocity","pre_effect_expression":"v.seen=v.secret+t.secret;v.parameter=7;"}},
                {"randomize":[{"weight":0,"log":"never"},{"weight":1,"log":"chosen"}]}]}
            """));
        e.context.setVariable("secret",42d);var velocity=new Vector3f(2,3,4);
        e.event(new JsonPrimitive("chain"),new Matrix4f().translation(10,20,30),velocity);
        assertEquals(List.of("sound:fixture.click","child:particle_with_velocity","log:chosen"),e.calls);
        assertEquals(new Vector3f(10,20,30),e.soundPosition);assertEquals(velocity,e.inherited);
        assertEquals(0d,e.childContext.variable("seen"));assertEquals(7d,e.childContext.variable("parameter"));assertEquals(0d,e.context.variable("parameter"));assertEquals(1d,e.context.variable("counter"));
    }
    @Test void childParticleWithoutVelocityDoesNotAccidentallyInheritAndCyclesAreBounded() {
        var e=new Emitter(effect("","\"child\":{\"particle_effect\":{\"effect\":\"fixture:child\",\"type\":\"particle\"}},\"recursive\":{\"sequence\":[\"recursive\"]}"));
        e.event(new JsonPrimitive("child"),new Matrix4f(),new Vector3f(1,2,3));assertEquals(new Vector3f(),e.inherited);
        assertDoesNotThrow(()->e.event(new JsonPrimitive("recursive"),new Matrix4f(),new Vector3f()));
    }
    @Test void travelDistanceAndLoopingThresholdsTriggerAcrossLargeMovement() {
        var e=new Emitter(effect("\"minecraft:emitter_lifetime_events\":{\"travel_distance_events\":{\"1\":\"once\"},\"looping_travel_distance_events\":[{\"distance\":0.5,\"effects\":[\"loop\"]}]}",
            "\"once\":{\"log\":\"once\"},\"loop\":{\"log\":\"loop\"}"));
        e.update(.05);e.frame.translation(1.2f,0,0);e.update(.05);e.update(.05);
        assertEquals(List.of("log:once","log:loop","log:loop"),e.calls);
    }
    @ParameterizedTest @ValueSource(strings={"point","box","sphere","disc","custom","entity_aabb"})
    void shapesRespectOffsetsDirectionsAndFiniteSampling(String shape) {
        var e=new Emitter(effect("\"minecraft:emitter_shape_"+shape+"\":{\"offset\":[2,3,4],\"direction\":[0,0,-1],\"radius\":2,\"half_dimensions\":[1,2,3],\"surface_only\":true,\"plane_normal\":\"x\"}",""));
        for(int i=0;i<50;i++) {
            var direction=new Vector3f();var p=e.shape(e.context,direction,new Vector3f(.5f,1,.5f));
            assertTrue(p.isFinite());assertEquals(new Vector3f(0,0,-1),direction);
            if(shape.equals("disc"))assertEquals(2,p.x,1e-6);
            if(shape.equals("point")||shape.equals("custom"))assertEquals(new Vector3f(2,3,4),p);
            if(shape.equals("sphere"))assertEquals(2,p.distance(new Vector3f(2,3,4)),1e-5);
        }
        if(shape.equals("entity_aabb")) {
            var noActor=e.shape(e.context,new Vector3f(),null);assertEquals(new Vector3f(2,3,4),noActor);
        }
    }
    @Test void reflectedRotatedVelocityInheritsWorldMotionWithoutScalingOrRotatingItTwice() {
        var d=effect("\"minecraft:particle_initial_speed\":[1,0,0]","");var c=new Molang.Context((q,a)->0d);
        var frame=new Matrix4f().rotateY((float)(Math.PI/2)).scale(1,1,-1);var inherited=new Vector3f(0,2,0);
        var world=BedrockParticleMotion.initialVelocity(d,c,new Vector3f(),frame,false,inherited,false,true);
        assertEquals(new Vector3f(1,0,0).mulDirection(frame).add(inherited),world);
        var local=BedrockParticleMotion.initialVelocity(d,c,new Vector3f(),frame,true,inherited,false,true);
        assertTrue(world.equals(frame.transformDirection(local),1e-5f));
    }
    @Test void dynamicAccelerationAndDragUseOldVelocityAndSpinIsInDegrees() {
        var d=effect("\"minecraft:particle_motion_dynamic\":{\"linear_acceleration\":[2,0,0],\"linear_drag_coefficient\":1,\"rotation_acceleration\":20,\"rotation_drag_coefficient\":1}","");
        var c=new Molang.Context((q,a)->0d);var v=new Vector3f(10,0,0);
        BedrockParticleMotion.dynamics(d,c,v,new Matrix4f(),false,.5);assertEquals(6,v.x,1e-6);
        assertEquals(15,BedrockParticleMotion.spin(d,c,10,.5),1e-9);
    }
    @Test void collisionDragIsAbsoluteSpeedLossAndObjectFormEventsWork() {
        var v=new Vector3f(0,-10,0);BedrockParticleMotion.collisionVelocity(v,false,true,false,.5,2,.5);assertEquals(4,v.y,1e-6);
        assertEquals(1,BedrockParticleMotion.collisionEvents(JsonParser.parseString("{\"events\":{\"event\":\"hit\"}}").getAsJsonObject()).size());
    }
    @Test void blockExpiryCanonicalizesVanillaNamesAndKillPlaneUsesWorldAxes() {
        var d=effect("\"minecraft:particle_expire_if_in_blocks\":[\"water\"],\"minecraft:particle_kill_plane\":[0,1,0,0]","");
        assertTrue(BedrockParticleMotion.expiresInBlock(d,"minecraft:water"));assertFalse(BedrockParticleMotion.expiresInBlock(d,"minecraft:air"));
        assertTrue(BedrockParticleMotion.expiresInBlock(effect("\"minecraft:particle_expire_if_not_in_blocks\":[\"air\"]",""),"minecraft:stone"));
        var c=new Molang.Context((q,a)->0d);assertEquals(-1,BedrockParticleMotion.planeDistance(d,c,new Vector3f(10,4,0),new Vector3f(10,5,0)));
        assertTrue(BedrockParticleMotion.crossesPlane(1,-1));assertTrue(BedrockParticleMotion.crossesPlane(-1,1));assertFalse(BedrockParticleMotion.crossesPlane(-1,-2));
    }
    @ParameterizedTest @ValueSource(strings={"rotate_xyz","rotate_y","lookat_xyz","lookat_y","lookat_direction","direction_x","direction_y","direction_z","emitter_transform_xy","emitter_transform_xz","emitter_transform_yz"})
    void allFacingModesProduceFiniteOrthonormalRotationsIncludingDegeneracy(String mode) {
        for(var direction:List.of(new Vector3f(),new Vector3f(1,2,3),new Vector3f(0,1,0))) {
            var q=BedrockParticleMotion.facing(mode,direction,new Vector3f(2,3,4),new Quaternionf().rotateY(.7f),new Matrix4f().scale(1,1,-1));
            assertTrue(q.isFinite());assertEquals(1,q.lengthSquared(),1e-5);
            if(direction.lengthSquared()>0&&(mode.startsWith("direction_")||mode.equals("lookat_direction"))) {
                var axis=mode.equals("direction_y")?new Vector3f(0,1,0):mode.equals("direction_z")?new Vector3f(0,0,1):new Vector3f(1,0,0);
                assertTrue(axis.rotate(q).distance(new Vector3f(direction).normalize())<1e-5,"Authored direction axis: "+mode);
            }
            if(mode.equals("lookat_xyz"))assertTrue(new Vector3f(0,0,1).rotate(q).distance(new Vector3f(2,3,4).normalize())<1e-5);
            if(mode.equals("rotate_y")||mode.equals("lookat_y"))assertEquals(1,new Vector3f(0,1,0).rotate(q).y,1e-5);
        }
    }
    @Test void rotateAndLookatAreDifferentAndDirectionThresholdPreservesLastHeading() {
        var view=new Vector3f(2,0,1);var camera=new Quaternionf();
        assertNotEquals(BedrockParticleMotion.facing("rotate_xyz",view,view,camera,new Matrix4f()),BedrockParticleMotion.facing("lookat_xyz",view,view,camera,new Matrix4f()));
        var d=effect("","");var c=new Molang.Context((q,a)->0d);
        assertEquals(new Vector3f(1,0,0),BedrockParticleMotion.direction(d,c,new Vector3f(.001f,0,0),new Vector3f(1,0,0)));
    }
    @Test void perRenderInitializationAffectsActualBillboardTintUvAndLightingButNotSimulationUpdates() {
        var d=effect("""
            "minecraft:particle_initialization":{"per_render_expression":"v.renders=v.renders+1;v.red=v.particle_age;"},
            "minecraft:particle_appearance_billboard":{"size":["v.particle_age",2],"uv":{"uv":[0,0],"uv_size":[0.5,1]}},
            "minecraft:particle_appearance_tinting":{"color":["v.red",0,0,0.5]},"minecraft:particle_appearance_lighting":{}
            ""","");
        var c=new Molang.Context((q,a)->0d);BedrockParticleMotion.appearance(d,c,.2,1,false);assertEquals(0d,c.variable("renders"));
        var a=BedrockParticleMotion.appearance(d,c,.5,1,true);
        assertArrayEquals(new double[]{.5,2},a.size());assertArrayEquals(new double[]{.5,0,0,.5},a.color());assertArrayEquals(new double[]{0,0,.5,1},a.uv());assertTrue(a.lit());
        assertEquals(1d,c.variable("renders"));assertFalse(BedrockParticleMotion.appearance(effect("",""),c,0,1,true).lit());
    }
    @Test void spinLifetimeAndParametricExpressionsEvaluateInParticleScope() {
        var d=effect("""
            "minecraft:particle_initial_spin":{"rotation":90,"rotation_rate":180},
            "minecraft:particle_lifetime_expression":{"max_lifetime":"v.duration","expiration_expression":"v.particle_age>0.5"},
            "minecraft:particle_motion_parametric":{"relative_position":["v.particle_age",1,0],"direction":[0,0,-1]}
            ""","");
        var c=new Molang.Context((q,a)->0d);c.setVariable("duration",2d);c.setVariable("particle_age",.25);
        assertEquals(90,BedrockParticleMotion.initialSpin(d,c));assertEquals(180,BedrockParticleMotion.initialSpinRate(d,c));assertEquals(2,BedrockParticleMotion.lifetime(d,c));
        assertEquals(new Vector3f(10.25f,21,30),BedrockParticleMotion.relativePosition(d,c,new Matrix4f().translation(10,20,30)));
        assertEquals(new Vector3f(0,0,-1),BedrockParticleMotion.direction(d,c,new Vector3f(1,0,0),new Vector3f()));
        assertFalse(BedrockParticleMotion.expired(d,c,.5,2));assertTrue(BedrockParticleMotion.expired(d,c,.6,2));
    }
}
