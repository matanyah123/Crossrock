package dev.matanyah.bedrockcompat.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.particle.SingleQuadParticle.Layer;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.joml.Vector3f;
import java.util.*;

/** Value-only extracted quads: Java's packed particle format only stores square sizes. */
public final class ParticleRectangles {
    private record Quad(Layer layer,List<Vector3f> points,float u0,float u1,float v0,float v1,int color,int light) {}
    private static final Map<QuadParticleRenderState,List<Quad>> FRAMES=new WeakHashMap<>();
    public static void clear(QuadParticleRenderState state){FRAMES.remove(state);}
    static void add(QuadParticleRenderState state,Layer layer,List<Vector3f> points,float u0,float u1,float v0,float v1,int color,int light) {
        state.add(layer,0,0,0,0,0,0,1,0,0,0,0,0,0,light);
        FRAMES.computeIfAbsent(state,s->new ArrayList<>()).add(new Quad(layer,points,u0,u1,v0,v1,color,light));
    }
    public static void build(QuadParticleRenderState state,Layer layer,VertexConsumer consumer) {
        for(var q:FRAMES.getOrDefault(state,List.of()))if(q.layer.equals(layer))for(int i=0;i<4;i++) {
            var p=q.points.get(i);
            consumer.addVertex(p.x,p.y,p.z).setUv(i<2?q.u1:q.u0,i==0||i==3?q.v1:q.v0).setColor(q.color).setLight(q.light);
        }
    }
}
