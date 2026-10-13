package dev.matanyah.bedrockcompat.bedrock.entity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BedrockMotionInputsTest {
    @Test void bedrockDayStartsAtMidnight() {
        assertEquals(0,BedrockMotionInputs.timeOfDay(18000));
        assertEquals(.25,BedrockMotionInputs.timeOfDay(0));
        assertEquals(.5,BedrockMotionInputs.timeOfDay(6000));
        assertEquals(.75,BedrockMotionInputs.timeOfDay(12000));
        assertEquals(.25,BedrockMotionInputs.timeOfDay(24000));
    }
    @Test void observedMotionDoesNotNeedServerVelocityOrDoubleCountRenderPasses() {
        var inputs=new BedrockMotionInputs();inputs.sample(0,0,0,0,true);
        var moving=inputs.sample(.05,.1,0,0,true);
        assertEquals(.1,moving.distance(),1e-8);assertEquals(2,moving.groundSpeed(),1e-8);
        assertEquals(moving,inputs.sample(.05,.1,0,0,true));
        var flying=inputs.sample(.1,.2,1,0,false);
        assertEquals(.2,flying.distance(),1e-8);assertEquals(.1,flying.walkDistance(),1e-8);
        assertEquals(0,inputs.sample(.15,100,0,0,true).groundSpeed());
    }
}
