package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockAnimationControllerTest {
    @Test void disabledPhysicsBranchDoesNotRunOrAffectCompletion() {
        var controller=new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"animations":[{"wrong":"0"},"active"]}}}
            """).getAsJsonObject());
        var calls=new ArrayList<String>();
        var frame=controller.createRuntime().sample(0,new Molang.Context((q,a)->0d),(alias,time,c)->{
            calls.add(alias);return sample(0,true);
        });
        assertEquals(List.of("active"),calls);assertTrue(frame.finished());
    }
    @Test void alternatingPhysicsControllerSamplesOnlyIncomingStateAfterSnapshot() {
        var controller=new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"animations":["physics"],"on_exit":["v.snapshot=v.snapshot+1;"],"transitions":[{"next":"1"}]},
             "next":{"animations":["physics"],"on_exit":["v.snapshot=v.snapshot+1;"],"transitions":[{"default":"1"}]}}}
            """).getAsJsonObject());
        var runtime=controller.createRuntime();var ctx=new Molang.Context((q,a)->0d);
        var count=new int[]{0};
        for(int i=0;i<5;i++) {
            final int expected=i+1;
            runtime.sample(i*.05,ctx,(alias,time,c)->{
                count[0]++;assertEquals((double)expected,c.variable("snapshot"));return sample(0,false);
            });
            assertEquals(expected,count[0],"Physics must integrate once per frame");
        }
    }
    @Test void stateExitStopsOldParticleParametersBeforeStartingNewOnes() {
        var controller=new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"particle_effects":[{"effect":"trail","pre_effect_script":"v.red=1;"}],
             "transitions":[{"ice":"q.state_time>0"}]},
             "ice":{"particle_effects":[{"effect":"trail","pre_effect_script":"v.red=0;"}]}}}
            """).getAsJsonObject());
        var runtime=controller.createRuntime();var actions=new java.util.ArrayList<BedrockEntityEffect>();
        var context=new Molang.Context((q,a)->0d);
        BedrockAnimationController.Sampler sampler=(alias,time,ctx)->sample(0,false);
        runtime.sample(0,context,sampler,actions::add);
        runtime.sample(1,context,sampler,actions::add);
        assertEquals(java.util.List.of(BedrockEntityEffect.Kind.PARTICLE,BedrockEntityEffect.Kind.PARTICLE_STOP,
            BedrockEntityEffect.Kind.PARTICLE),actions.stream().map(BedrockEntityEffect::kind).toList());
        assertEquals("v.red=1;",actions.get(1).preEffectScript());
        assertEquals("v.red=0;",actions.get(2).preEffectScript());
        runtime.sample(1,context,sampler,actions::add);
        assertEquals(3,actions.size());
    }
    @Test void revisitingOlderRenderTimestampKeepsStateWithoutReplayingEvents() {
        var controller=new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"on_entry":["v.entries=v.entries+1;"],"animations":["idle"]}}}
            """).getAsJsonObject());
        var runtime=controller.createRuntime();var ctx=new Molang.Context((q,a)->0d);
        BedrockAnimationController.Sampler sampler=(a,t,c)->sample(t,false);
        var latest=runtime.sample(10,ctx,sampler);
        assertEquals(latest,runtime.sample(9.9,ctx,sampler));
        assertEquals(1d,ctx.variable("entries"));
        assertEquals(.1,x(runtime.sample(10.1,ctx,sampler)),1e-8);
    }
    @Test void remapVariablesAreScopedAndCurveBlendsUseOutgoingWeight() {
        var controller=new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"variables":{"speed":{"input":"0.5","remap_curve":{"0":2,"1":4}}},
             "animations":[{"idle":"v.speed"}],"blend_transition":{"0":1,"0.5":0.25,"1":0},
             "transitions":[{"next":"v.speed==3 && q.state_time>=1"}]},"next":{"animations":["move"]}}}
            """).getAsJsonObject());
        var context=new Molang.Context((q,a)->0d);context.setVariable("speed",99d);
        var runtime=controller.createRuntime();
        BedrockAnimationController.Sampler sampler=(a,t,c)->sample(a.equals("idle")?2:10,false);
        assertEquals(6,x(runtime.sample(0,context,sampler)));
        assertEquals(99d,context.variable("speed"));
        assertEquals(6,x(runtime.sample(1,context,sampler)));
        assertEquals(9,x(runtime.sample(1.5,context,sampler)));
        assertEquals(10,x(runtime.sample(2,context,sampler)));
    }
    @Test void entryEffectsAreIndependentAndDoNotRepeatOnDuplicateFrames() {
        var controller=new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"sound_effects":[{"effect":"voice"}],
             "particle_effects":[{"effect":"spark","locator":"hand","pre_effect_script":"v.started=1;"}],
             "transitions":[{"next":"q.state_time>1"}]},"next":{"sound_effects":[{"effect":"end"}]}}}
            """).getAsJsonObject());
        var runtime=controller.createRuntime();var context=new Molang.Context((q,a)->0d);
        List<BedrockEntityEffect> actions=new ArrayList<>();
        BedrockAnimationController.Sampler sampler=(a,t,c)->new BedrockAnimationController.Sample(Map.of(),false);
        runtime.sample(0,context,sampler,actions::add);runtime.sample(0,context,sampler,actions::add);
        assertEquals(2,actions.size());assertEquals(0d,context.variable("started"),"Particle startup script belongs to its emitter, not the actor");
        assertTrue(actions.get(1).bindToActor());assertTrue(actions.get(1).managed());
        assertEquals("hand",actions.get(1).locator());
        assertEquals("v.started=1;",actions.get(1).preEffectScript());
        runtime.sample(2,context,sampler,actions::add);
        assertEquals(4,actions.size());assertEquals("end",actions.getLast().effect());
        assertEquals(BedrockEntityEffect.Kind.PARTICLE_STOP,actions.get(2).kind());
        assertEquals("hand",actions.get(2).locator());
        assertEquals("v.started=1;",actions.get(2).preEffectScript());
    }
    @Test void unboundControllerFlagSurvivesStartAndStop() {
        var controller=new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"particle_effects":[{"effect":"spark","bind_to_actor":false,"locator":"hand"}],
            "transitions":[{"next":"q.state_time>0"}]},"next":{}}}
            """).getAsJsonObject());
        var runtime=controller.createRuntime();var context=new Molang.Context((q,a)->0d);var actions=new ArrayList<BedrockEntityEffect>();
        BedrockAnimationController.Sampler sampler=(a,t,c)->new BedrockAnimationController.Sample(Map.of(),false);
        runtime.sample(0,context,sampler,actions::add);runtime.sample(1,context,sampler,actions::add);
        assertEquals(2,actions.size());assertFalse(actions.getFirst().bindToActor());assertFalse(actions.getLast().bindToActor());
        assertEquals(BedrockEntityEffect.Kind.PARTICLE_STOP,actions.getLast().kind());
    }
    @Test void orderedTransitionRunsOnceAndBlendsOverSeconds() {
        var controller = new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"animations":["idle"],"blend_transition":2,
             "on_entry":["v.entered=v.entered+1;"],"on_exit":["v.exited=v.exited+1;"],
             "transitions":[{"moving":"q.state_time >= 1"},{"wrong":"q.state_time >= 1"}]},
             "moving":{"animations":["move"],"on_entry":["v.entered=v.entered+1;"]},"wrong":{}}}
            """).getAsJsonObject());
        // Both transitions become true together: the first matching transition must win.
        var context = new Molang.Context((q,a)->0d);
        var runtime = controller.createRuntime();
        BedrockAnimationController.Sampler sampler = (alias,time,ctx)->sample(alias.equals("move")?10:0,false);
        runtime.sample(0,context,sampler);
        runtime.sample(1,context,sampler);
        assertEquals("moving",runtime.state());
        assertEquals(2d,context.variable("entered"));
        assertEquals(1d,context.variable("exited"));
        // Separate controller without the always-true alternative for timing/duplicate-frame assertions.
        controller = new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"animations":["idle"],"blend_transition":2,
             "on_entry":["v.entries=v.entries+1;"],"transitions":[{"moving":"q.state_time >= 1"}]},
             "moving":{"animations":["move"],"on_entry":["v.entries=v.entries+1;"]}}}
            """).getAsJsonObject());
        runtime=controller.createRuntime();
        assertEquals(0,x(runtime.sample(0,context,sampler)));
        assertEquals(0,x(runtime.sample(1,context,sampler)));
        assertEquals("moving",runtime.state());
        assertEquals(2d,context.variable("entries"));
        assertEquals(5,x(runtime.sample(2,context,sampler)));
        assertEquals(5,x(runtime.sample(2,context,sampler)));
        assertEquals(2d,context.variable("entries"));
        assertEquals(10,x(runtime.sample(3,context,sampler)));
    }
    @Test void completionQueriesAndStateClockAreScoped() {
        var controller=new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"animations":["short","long"],
            "transitions":[{"done":"q.all_animations_finished"},{"partial":"q.any_animation_finished"}]},
            "done":{},"partial":{}}}
            """).getAsJsonObject());
        var context=new Molang.Context((q,a)->99d);
        var runtime=controller.createRuntime();
        runtime.sample(0,context,(alias,time,ctx)->sample(0,alias.equals("short")));
        assertEquals("partial",runtime.state());
        assertEquals(99d,Molang.compile("q.state_time").evaluate(context));
    }
    @Test void missingTargetsFailAtCompileTime() {
        assertThrows(IllegalArgumentException.class,()->new BedrockAnimationController(JsonParser.parseString("""
            {"states":{"default":{"transitions":[{"absent":"1"}]}}}
            """).getAsJsonObject()));
    }
    private static BedrockAnimationController.Sample sample(double x,boolean finished) {
        return new BedrockAnimationController.Sample(Map.of("bone",new BedrockAnimation.Pose(
            new BedrockAnimation.Vector(x,0,0),new BedrockAnimation.Vector(0,0,0),new BedrockAnimation.Vector(1,1,1))),finished);
    }
    private static double x(BedrockAnimationController.Sample sample) {return sample.poses().get("bone").position().x();}
}
