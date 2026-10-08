package dev.matanyah.bedrockcompat.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockArmorStandStateTest {
    @Test void cyclingWrapsAndDoesNotActivateVanillaStands() {
        var state = new BedrockArmorStandState();
        for (int i=0;i<13;i++) state.cycle();
        assertEquals(0,state.poseIndex());
        assertFalse(state.active());
        state.poseIndex(-1);assertEquals(12,state.poseIndex());
    }
    @Test void activationPreservesOriginalArmSettingAcrossRepeatedTicks() {
        var state = new BedrockArmorStandState();
        state.activate(false);state.activate(true);
        assertFalse(state.originalArms());
        state.poseIndex(5);state.deactivate();
        assertEquals(5,state.poseIndex());
        var restored = new BedrockArmorStandState();
        restored.restore(state.active(),state.poseIndex(),state.originalArms());
        assertFalse(restored.active());assertEquals(5,restored.poseIndex());
    }
}
