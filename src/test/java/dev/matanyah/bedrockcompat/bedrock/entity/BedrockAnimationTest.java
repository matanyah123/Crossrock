package dev.matanyah.bedrockcompat.bedrock.entity;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockAnimationTest {
    @Test void runtimeCustomClockTimelineAndWeightsAreNotFrameCounters() {
        var animation=new BedrockAnimation(JsonParser.parseString("""
            {"loop":true,"animation_length":2,"anim_time_update":"q.anim_time+q.delta_time*0.5", "blend_weight":0.5,
             "timeline":{"0":"v.entries=v.entries+1;","0.5":"v.events=v.events+1;"},
             "bones":{"head":{"position":["q.anim_time",0,0]}}}
            """).getAsJsonObject());
        var runtime=animation.createRuntime();var context=new Molang.Context((q,a)->0d);
        var zero=runtime.sample(0,context,java.util.Map.of(),e->{});
        assertEquals(1d,context.variable("entries"));
        var frame=runtime.sample(1,context,java.util.Map.of(),e->{});
        assertEquals(.25,frame.poses().get("head").position().x());assertEquals(1d,context.variable("events"));
        runtime.sample(1,context,java.util.Map.of(),e->{});assertEquals(1d,context.variable("events"));
        runtime.sample(4,context,java.util.Map.of(),e->{});assertEquals(2d,context.variable("entries"));
    }
    @Test void delayAndLoopDelayHoldFramesAndDoNotRepeatEvents() {
        var animation=new BedrockAnimation(JsonParser.parseString("""
            {"loop":true,"animation_length":1,"start_delay":1,"loop_delay":1,
             "timeline":{"0":"v.entries=v.entries+1;"},"bones":{"head":{"position":["q.anim_time",0,0]}}}
            """).getAsJsonObject());
        var runtime=animation.createRuntime();var ctx=new Molang.Context((q,a)->0d);
        assertTrue(runtime.sample(0,ctx,java.util.Map.of(),e->{}).poses().isEmpty());
        assertEquals(0,runtime.sample(1,ctx,java.util.Map.of(),e->{}).poses().get("head").position().x());
        assertEquals(1,runtime.sample(2,ctx,java.util.Map.of(),e->{}).poses().get("head").position().x());
        assertEquals(1,runtime.sample(2.5,ctx,java.util.Map.of(),e->{}).poses().get("head").position().x());
        assertEquals(0,runtime.sample(3,ctx,java.util.Map.of(),e->{}).poses().get("head").position().x());
        assertEquals(2d,ctx.variable("entries"));
    }
    @Test void thisReadsThePrecedingChannelNotAFabricatedZero() {
        var animation=new BedrockAnimation(JsonParser.parseString("""
            {"bones":{"body":{"rotation":["-this","this+5",0],"scale":"this*2"}}}
            """).getAsJsonObject());
        var previous=new BedrockAnimation.Pose(new BedrockAnimation.Vector(0,0,0),new BedrockAnimation.Vector(90,20,0),new BedrockAnimation.Vector(1,2,3));
        var pose=animation.sample(0,new Molang.Context((q,a)->0d),java.util.Map.of("body",previous)).get("body");
        assertEquals(-90,pose.rotation().x());assertEquals(25,pose.rotation().y());
        assertEquals(new BedrockAnimation.Vector(2,4,6),pose.scale());
    }
    @Test void proceduralClipKeepsRunningAndOwnsItsAnimationTime() {
        var animation=new BedrockAnimation(JsonParser.parseString("""
            {"bones":{"head":{"rotation":[0,"q.anim_time * 10",0]}}}
            """).getAsJsonObject());
        var context=new Molang.Context((q,a)->999d);
        assertEquals(30,animation.sample(3,context).get("head").rotation().y());
        assertEquals(999d,Molang.compile("q.anim_time").evaluate(context));
        assertFalse(animation.finished(3));
    }
    @Test void catmullRomUsesOutgoingKeyMode() {
        var animation=new BedrockAnimation(JsonParser.parseString("""
            {"loop":"hold_on_last_frame","bones":{"head":{"position":{
             "0":[0,0,0],"1":{"post":[2,0,0],"lerp_mode":"catmullrom"},
             "2":[2,0,0],"3":[0,0,0]}}}}
            """).getAsJsonObject());
        assertEquals(2.25,animation.sample(1.5,new Molang.Context((q,a)->0d)).get("head").position().x(),1e-9);
    }
    @Test void timeIsSecondsAndLoopDoesNotDependOnFrameRate() {
        var animation=new BedrockAnimation(JsonParser.parseString("""
            {"loop":true,"animation_length":2,"bones":{"head":{"rotation":{"0":[0,0,0],"2":[0,90,0]}}}}
            """).getAsJsonObject());
        var context=new Molang.Context((q,a)->0d);
        assertEquals(45,animation.sample(1,context).get("head").rotation().y());
        assertEquals(animation.sample(1,context),animation.sample(3,context));
        assertEquals(1,animation.sample(1,context).get("head").scale().x());
    }
    @Test void discontinuousKeyUsesPreBeforeAndPostAtKey() {
        var animation=new BedrockAnimation(JsonParser.parseString("""
            {"loop":"hold_on_last_frame","bones":{"head":{"position":{"0":[0,0,0],"1":{"pre":[2,0,0],"post":[8,0,0]}}}}}
            """).getAsJsonObject());
        var context=new Molang.Context((q,a)->0d);
        assertEquals(1,animation.sample(.5,context).get("head").position().x());
        assertEquals(8,animation.sample(1,context).get("head").position().x());
        assertEquals(8,animation.sample(10,context).get("head").position().x());
    }
}
