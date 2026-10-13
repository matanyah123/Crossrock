package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockParticleEffectTest {
    private static BedrockParticleEffect effect(String extra) {
        return new BedrockParticleEffect(JsonParser.parseString("{\"description\":{\"identifier\":\"test:effect\",\"basic_render_parameters\":{\"material\":\"particles_alpha\",\"texture\":\"textures/particle/sheet\"}},"+extra+"}").getAsJsonObject());
    }
    @Test void animatedAtlasUvsAndArgbGradientUseAuthoredFrames() {
        var effect=effect("""
            "components":{"minecraft:particle_appearance_billboard":{"uv":{"texture_width":144,"texture_height":32,
              "flipbook":{"base_UV":[0,0],"size_UV":[16,16],"step_UV":[16,0],"max_frame":9,"stretch_to_lifetime":true}}},
              "minecraft:particle_appearance_tinting":{"color":{"interpolant":"v.particle_age","gradient":{"0":"#FFFF0000","1":"#000000FF"}}}}
            """);
        var context=new Molang.Context((q,a)->0d);context.setVariable("particle_age",.5);
        assertArrayEquals(new double[]{64/144d,0,80/144d,.5},effect.uv(context,.5,1),1e-9);
        assertArrayEquals(new double[]{.5,0,.5,.5},effect.color(effect.component("particle_appearance_tinting").get("color"),context),1e-9);
    }
    @Test void curveFamiliesEvaluateAndKeepIndependentParticleVariables() {
        var effect=effect("""
            "curves":{"v.linear":{"type":"linear","input":"v.particle_age","nodes":[0,2]},
            "v.bezier":{"type":"bezier","input":"v.particle_age","nodes":[0,0,1,1]},
            "v.catmull":{"type":"catmull_rom","input":"v.particle_age","nodes":[0,0,1,1]},
            "v.chain":{"type":"bezier_chain","input":"v.particle_age","nodes":{"0":{"value":0,"slope":0},"1":{"value":1,"slope":0}}}}
            """);
        var context=new Molang.Context((q,a)->0d);context.setVariable("particle_age",.5);effect.curves(context);
        assertEquals(1d,context.variable("linear"));
        for(String name:new String[]{"bezier","catmull","chain"})assertEquals(.5,(double)context.variable(name),1e-9);
        var other=new Molang.Context((q,a)->0d);other.setVariable("particle_age",0d);effect.curves(other);
        assertEquals(0d,other.variable("linear"));assertEquals(1d,context.variable("linear"));
    }
    @Test void particleDefinitionNamespaceCannotClobberEntityAndUnknownJavaEffectsStayUnmapped() {
        assertEquals("minecraft:basic_crit_particle",BedrockParticleIds.bedrock("minecraft:crit"));
        assertEquals("other:custom",BedrockParticleIds.bedrock("other:custom"));
        assertEquals("minecraft:block",BedrockParticleIds.bedrock("minecraft:block"));
    }
}
