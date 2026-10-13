package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Live particle-system observations; actor-specific queries delegate to the originating actor. */
public record ParticleQueryContext(Molang.Queries actor, Supplier<double[]> emitterPosition,
                                   Supplier<double[]> cameraPosition, IntSupplier effectEmitters,
                                   IntSupplier effectParticles) implements Molang.Queries {
    @Override public Object get(String name,List<Object> args) {
        return switch(name) {
            case "effect_emitter_count" -> (double)effectEmitters.getAsInt();
            case "effect_particle_count" -> (double)effectParticles.getAsInt();
            case "distance_from_camera" -> {
                double[] p=emitterPosition.get(),c=cameraPosition.get();
                yield Math.sqrt(Math.pow(p[0]-c[0],2)+Math.pow(p[1]-c[1],2)+Math.pow(p[2]-c[2],2));
            }
            default -> actor.get(name,args);
        };
    }
}
