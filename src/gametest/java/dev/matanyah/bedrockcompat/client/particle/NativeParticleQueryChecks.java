package dev.matanyah.bedrockcompat.client.particle;

import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import java.util.*;

/** Test-only access to live registries; no production testing hooks or installed pack edits. */
public final class NativeParticleQueryChecks {
    private NativeParticleQueryChecks() {}
    @SuppressWarnings("unchecked")
    public static void verify() {
        try {
            var all=new ArrayList<NativeParticles.Emitter>();
            for(String name:List.of("emitters","pending")) {
                var field=NativeParticles.class.getDeclaredField(name);field.setAccessible(true);
                all.addAll((List<NativeParticles.Emitter>)field.get(null));
            }
            int checked=0;
            for(var e:all)if(Set.of("fixture:bound","fixture:unbound","fixture:mojang_basic_flame").contains(e.definition.id)) {
                double emitters=all.stream().filter(other->!other.finished&&other.definition.id.equals(e.definition.id)).count();
                double particles=all.stream().filter(other->other.definition.id.equals(e.definition.id)).mapToInt(other->other.count).sum();
                if(!e.queries.get("effect_emitter_count",List.of()).equals(emitters)||!e.queries.get("effect_particle_count",List.of()).equals(particles))
                    throw new AssertionError("Live effect counts disagree: "+e.definition.id);
                var at=e.transform().getTranslation(new Vector3f());var camera=Minecraft.getInstance().gameRenderer.mainCamera().position();
                double distance=Math.sqrt(Math.pow(at.x-camera.x,2)+Math.pow(at.y-camera.y,2)+Math.pow(at.z-camera.z,2));
                if(Math.abs((double)e.queries.get("distance_from_camera",List.of())-distance)>1e-7)throw new AssertionError("Emitter distance used actor root");
                checked++;
            }
            if(checked<3)throw new AssertionError("Standalone and both bound/unbound query contexts were not exercised");
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
}
