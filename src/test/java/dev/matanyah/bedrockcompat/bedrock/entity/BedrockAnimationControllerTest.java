package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockAnimationControllerTest {
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
        assertEquals(2,actions.size());assertEquals(1d,context.variable("started"));
        assertEquals("hand",actions.get(1).locator());
        runtime.sample(2,context,sampler,actions::add);
        assertEquals(3,actions.size());assertEquals("end",actions.getLast().effect());
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
