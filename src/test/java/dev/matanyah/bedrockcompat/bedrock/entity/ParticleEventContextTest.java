package dev.matanyah.bedrockcompat.bedrock.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParticleEventContextTest {
    @Test void ribbonFacesCameraRatherThanLyingSideways() {
        var direction=new org.joml.Vector3f(1,0,0);var view=new org.joml.Vector3f(0,1,3);
        var rotation=BedrockParticleEffect.trailRotation(direction,view);
        assertTrue(new org.joml.Vector3f(1,0,0).rotate(rotation).dot(direction)>.999f);
        assertTrue(new org.joml.Vector3f(0,0,1).rotate(rotation).dot(view.normalize())>.999f);
        assertTrue(new org.joml.Vector3f(0,0,1).rotate(BedrockParticleEffect.trailRotation(direction,direction)).isFinite());
    }
    @Test void independentEmitterContextCannotReadParentVariablesOrTemporaries() {
        var parent=new Molang.Context((q,a)->0d);
        Molang.compile("v.previous=3;t.distance=5;").evaluate(parent);
        var child=new Molang.Context((q,a)->0d);
        Molang.compile("v.previous=t.distance;v.child=1;").evaluate(child);
        assertEquals(0d,Molang.number(child.variable("previous")));
        assertEquals(3d,Molang.number(parent.variable("previous")));
        assertEquals(0d,Molang.number(parent.variable("child")));
        child.beginFrame();
        assertEquals(0d,Molang.number(Molang.compile("t.distance").evaluate(child)));
        assertEquals(5d,Molang.number(Molang.compile("t.distance").evaluate(parent)));
    }
}
