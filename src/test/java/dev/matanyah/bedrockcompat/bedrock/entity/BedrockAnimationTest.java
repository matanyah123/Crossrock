package dev.matanyah.bedrockcompat.bedrock.entity;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockAnimationTest {
    @Test void newPhysicsClipReceivesFrameDeltaEvenAtStateTimeZero() {
        var clip=new BedrockAnimation(JsonParser.parseString("{\"loop\":true,\"anim_time_update\":\"v.step=q.delta_time;return 0;\"}").getAsJsonObject());
        var context=new Molang.Context((q,a)->0d);
        clip.createRuntime().sample(0,context,java.util.Map.of(),effect->{},.05);
        assertEquals(.05,context.variable("step"));
    }
    @Test void boneExpressionsKeepAuthoredVariableDependencyOrder() {
        var animation=new BedrockAnimation(JsonParser.parseString("""
            {"bones":{"source":{"position":["v.anchor=7;",0,0]},
             "destination":{"position":["v.anchor",0,0]}}}
            """).getAsJsonObject());
        assertEquals(7,animation.sample(0,new Molang.Context((q,a)->0d)).get("destination").position().x());
    }
    @Test void absoluteClockDoesNotReplayOldLoopsOrFailAfterLongUptime() {
        var animation=new BedrockAnimation(JsonParser.parseString("""
            {"loop":true,"animation_length":0.1,"anim_time_update":"q.life_time",
             "timeline":{"0.05":"v.events=v.events+1;"},
             "bones":{"head":{"position":["q.anim_time",0,0]}}}
            """).getAsJsonObject());
        double[] clock={0};var ctx=new Molang.Context((q,a)->clock[0]);var runtime=animation.createRuntime();
        runtime.sample(0,ctx,java.util.Map.of(),e->{});
        for(int i=1;i<=2000;i++) {clock[0]=i*.05;runtime.sample(clock[0],ctx,java.util.Map.of(),e->{});}
        assertEquals(1000,(double)ctx.variable("events"),1);
        clock[0]=36000.025;
        assertDoesNotThrow(()->runtime.sample(clock[0],ctx,java.util.Map.of(),e->{}));
        clock[0]+=.01;
        assertEquals(.035,runtime.sample(clock[0],ctx,java.util.Map.of(),e->{}).poses().get("head").position().x(),1e-7);
    }
    @Test void singleComponentArraysBroadcastAndPreservePerAxisThis() {
        var animation=new BedrockAnimation(JsonParser.parseString("""
            {"bones":{"body":{"position":[3],"rotation":["this+2"],"scale":[2]}}}
            """).getAsJsonObject());
        var previous=new BedrockAnimation.Pose(new BedrockAnimation.Vector(0,0,0),
            new BedrockAnimation.Vector(10,20,30),new BedrockAnimation.Vector(1,1,1));
        var pose=animation.sample(0,new Molang.Context((q,a)->0d),java.util.Map.of("body",previous)).get("body");
        assertEquals(new BedrockAnimation.Vector(3,3,3),pose.position());
        assertEquals(new BedrockAnimation.Vector(12,22,32),pose.rotation());
        assertEquals(new BedrockAnimation.Vector(2,2,2),pose.scale());
        for(String invalid:java.util.List.of("[]","[1,2]","[1,2,3,4]"))
            assertThrows(IllegalArgumentException.class,()->new BedrockAnimation(JsonParser.parseString(
                "{\"bones\":{\"body\":{\"position\":"+invalid+"}}}").getAsJsonObject()));
    }

    @Test void keyframeRatioIsChannelLocalAndRestoresCallerQueries() {
        var animation=new BedrockAnimation(JsonParser.parseString("""
            {"loop":"hold_on_last_frame","bones":{"body":{
              "position":{"0":["q.key_frame_lerp_time",0,0],"2":["query.key_frame_lerp_time",0,0]},
              "rotation":{"0":["global.key_frame_lerp_time",0,0],"4":["global.key_frame_lerp_time",0,0]},
              "scale":[2]}}}
            """).getAsJsonObject());
        var context=new Molang.Context((q,a)->999d);
        var pose=animation.sample(1,context).get("body");
        assertEquals(.5,pose.position().x());
        assertEquals(.25,pose.rotation().x());
        assertEquals(999d,Molang.compile("q.key_frame_lerp_time").evaluate(context));
        context.withQueries(java.util.Map.of("key_frame_lerp_time",7d),()-> {
            assertEquals(.75,animation.sample(1.5,context).get("body").position().x());
            assertEquals(7d,Molang.compile("q.key_frame_lerp_time").evaluate(context));return null;
        });
        assertEquals(1,animation.sample(4,context).get("body").position().x());
    }

    @Test void keyframeRatioWorksWithSmoothInterpolationAndRestoresAfterErrors() {
        var animation=new BedrockAnimation(JsonParser.parseString("""
            {"bones":{"body":{"position":{
              "0":["q.key_frame_lerp_time"],
              "1":{"post":["q.key_frame_lerp_time"],"lerp_mode":"catmullrom"},
              "2":["q.key_frame_lerp_time"],"3":["q.key_frame_lerp_time"]}}}}
            """).getAsJsonObject());
        var context=new Molang.Context((q,a)->{throw new IllegalArgumentException("Missing query: "+q);});
        assertEquals(new BedrockAnimation.Vector(.25,.25,.25),animation.sample(1.25,context).get("body").position());
        var broken=new BedrockAnimation(JsonParser.parseString("""
            {"bones":{"body":{"position":{"0":["q.key_frame_lerp_time+q.missing"],"1":[0]}}}}
            """).getAsJsonObject());
        assertThrows(IllegalArgumentException.class,()->broken.sample(.5,context));
        assertThrows(IllegalArgumentException.class,()->Molang.compile("q.key_frame_lerp_time").evaluate(context));
    }

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
