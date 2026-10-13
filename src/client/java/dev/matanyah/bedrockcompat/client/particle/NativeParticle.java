package dev.matanyah.bedrockcompat.client.particle;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.entity.*;
import dev.matanyah.bedrockcompat.client.entity.NativeEntityResources;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Bedrock components simulate in seconds; Java collision and batching remain in charge of the world. */
final class NativeParticle extends SingleQuadParticle {
    private final NativeParticles.Emitter emitter;
    private final BedrockParticleEffect definition;
    private final Molang.Context context;
    private final Matrix4f birth;
    private final Vector3f local,velocity;
    private final boolean localPosition,localRotation,lit;
    private final boolean parametricAllowed;
    private final double seconds;
    private double spin;
    private double planeDistance;
    private Vector3f facingDirection=new Vector3f(0,1,0);
    private double[] uv={0,0,1,1};
    private double[] dimensions={.1,.1};
    private final int generation=NativeParticles.generation;
    NativeParticle(ClientLevel level,NativeParticles.Emitter emitter,TextureAtlasSprite sprite,Vector3f suppliedVelocity,boolean vanilla,boolean inherited) {
        super(level,0,0,0,sprite);this.emitter=emitter;definition=emitter.definition;
        context=new Molang.Context((q,a)->emitter.queries.get(q,a),emitter.random.nextLong());
        emitter.context.variables().forEach(context::setVariable);
        for(int i=1;i<=4;i++) {
            context.setVariable("particle_random_"+i,emitter.random.nextDouble());
            context.setVariable("emitter_random_"+i,emitter.context.variable("emitter_random_"+i));
        }
        context.setVariable("particle_age",0d);context.setVariable("emitter_age",emitter.age);
        context.setVariable("emitter_lifetime",emitter.lifetime);definition.curves(context);
        definition.script(definition.component("particle_initialization"),"creation_expression",context);
        seconds=BedrockParticleMotion.lifetime(definition,context);
        context.setVariable("particle_lifetime",seconds);lifetime=(int)Math.min(12000,Math.ceil(seconds*20));
        birth=new Matrix4f(emitter.transform());velocity=new Vector3f();local=emitter.shape(context,velocity);
        var space=definition.component("emitter_local_space");
        localPosition=emitter.owner.attached&&definition.value(space,"position",context,0)!=0;
        localRotation=localPosition&&definition.value(space,"rotation",context,0)!=0;
        parametricAllowed=!vanilla&&!emitter.manualEvent&&(!emitter.owner.attached||localPosition);
        lit=definition.has("particle_appearance_lighting");hasPhysics=definition.has("particle_motion_collision");
        velocity.set(BedrockParticleMotion.initialVelocity(definition,context,velocity,birth,localRotation,suppliedVelocity,vanilla,inherited));
        if(definition.value(space,"velocity",context,0)!=0&&emitter.owner.entity.get()!=null) {
            var v=emitter.owner.entity.get().getDeltaMovement();var extra=new Vector3f((float)v.x*20,(float)v.y*20,(float)v.z*20);
            if(localRotation)new Matrix4f(birth).invert().transformDirection(extra);
            velocity.add(extra);
        }
        roll=(float)Math.toRadians(BedrockParticleMotion.initialSpin(definition,context));oRoll=roll;
        spin=BedrockParticleMotion.initialSpinRate(definition,context);
        var point=birth.transformPosition(new Vector3f(local));
        if(definition.has("emitter_shape_entity_aabb")) {
            point.set(local).add(birth.getTranslation(new Vector3f()));
            if(localPosition)new Matrix4f(birth).invert().transformPosition(point,local);
        }
        setPos(point.x,point.y,point.z);xo=x;yo=y;zo=z;
        planeDistance=BedrockParticleMotion.planeDistance(definition,context,point,birth.getTranslation(new Vector3f()));
        emitter.event(definition.component("particle_lifetime_events").get("creation_event"),new Matrix4f().translation(point),worldVelocity());
        emitter.timeline(definition.component("particle_lifetime_events"),-1,0,new Matrix4f().translation(point),worldVelocity());
        appearance(0);
        if(seconds==0)remove(); // Event-only particles expire at birth, not a tick later.
    }
    @Override public void tick() {
        if(removed)return;
        if(generation!=NativeParticles.generation){super.remove();return;}
        try {
            xo=x;yo=y;zo=z;oRoll=roll;
            double previous=age/20d,now=++age/20d;
            context.beginFrame();context.setVariable("particle_age",now);context.setVariable("emitter_age",emitter.age);
            definition.curves(context);definition.script(definition.component("particle_initialization"),"per_update_expression",context);
            var life=definition.component("particle_lifetime_expression");
            if(definition.value(life,"expiration_expression",context,0)!=0||expiresInBlock()){remove();return;}
            BedrockParticleMotion.dynamics(definition,context,velocity,birth,localRotation,.05);
            spin=BedrockParticleMotion.spin(definition,context,spin,.05);
            roll+=Math.toRadians(spin*.05);
            var parametric=definition.component("particle_motion_parametric");
            if(parametricAllowed&&parametric.has("rotation"))roll=(float)Math.toRadians(definition.value(parametric,"rotation",context,Math.toDegrees(roll)));
            if(parametricAllowed&&definition.has("particle_motion_parametric")) {
                var point=BedrockParticleMotion.relativePosition(definition,context,new Matrix4f(localPosition?emitter.transform():birth));
                setPos(point.x,point.y,point.z);
            } else if(localPosition) {
                var delta=new Vector3f(velocity).mul(.05f);
                if(!localRotation)new Matrix4f(birth).invert().transformDirection(delta);
                local.add(delta);
                var matrix=localRotation?emitter.transform():new Matrix4f(birth).setTranslation(emitter.transform().getTranslation(new Vector3f()));
                var point=matrix.transformPosition(new Vector3f(local));setPos(point.x,point.y,point.z);
            } else {
                double px=x,py=y,pz=z;var collision=definition.component("particle_motion_collision");
                hasPhysics=definition.value(collision,"enabled",context,1)!=0&&definition.has("particle_motion_collision");
                float radius=(float)Math.clamp(definition.value(collision,"collision_radius",context,.01),0,.5);setSize(radius*2,radius*2);
                // Bedrock's radius surrounds the particle centre; Java's usual
                // particle box starts at y. Keep contact decals above the surface.
                setBoundingBox(new net.minecraft.world.phys.AABB(x-radius,y-radius,z-radius,x+radius,y+radius,z+radius));
                move(velocity.x*.05,velocity.y*.05,velocity.z*.05);
                var moved=getBoundingBox().getCenter();setPos(moved.x,moved.y,moved.z);
                boolean hit=Math.abs(x-px-velocity.x*.05)>1e-5||Math.abs(y-py-velocity.y*.05)>1e-5||Math.abs(z-pz-velocity.z*.05)>1e-5;
                if(hit) {
                    if(collision.has("events"))for(var event:BedrockParticleMotion.collisionEvents(collision)) {
                        var entry=event.getAsJsonObject();
                        if(velocity.length()>=Math.max(2,definition.value(entry,"min_speed",context,2)))
                            emitter.event(entry.get("event"),new Matrix4f().translation((float)x,(float)y,(float)z),worldVelocity());
                    }
                    double impactSpeed=velocity.length();
                    float bounce=(float)definition.value(collision,"coefficient_of_restitution",context,0);
                    BedrockParticleMotion.collisionVelocity(velocity,Math.abs(x-px-velocity.x*.05)>1e-5,
                        Math.abs(y-py-velocity.y*.05)>1e-5,Math.abs(z-pz-velocity.z*.05)>1e-5,bounce,
                        definition.value(collision,"collision_drag",context,0),.05);
                    if(definition.value(collision,"expire_on_contact",context,0)!=0){
                        double distance=Math.sqrt((x-px)*(x-px)+(y-py)*(y-py)+(z-pz)*(z-pz));
                        context.setVariable("particle_age",previous+Math.min(.05,distance/Math.max(1e-9,impactSpeed)));
                        remove();return;
                    }
                }
            }
            if(definition.has("particle_kill_plane")) {
                double nowDistance=BedrockParticleMotion.planeDistance(definition,context,new Vector3f((float)x,(float)y,(float)z),emitter.transform().getTranslation(new Vector3f()));
                if(BedrockParticleMotion.crossesPlane(planeDistance,nowDistance)){remove();return;}
                planeDistance=nowDistance;
            }
            emitter.timeline(definition.component("particle_lifetime_events"),previous,Math.min(now,seconds),new Matrix4f().translation((float)x,(float)y,(float)z),worldVelocity());
            appearance(now);
            if(age>=lifetime)remove();
        } catch(RuntimeException failure){NativeEntityResources.runtimeError("Particle "+definition.id+": "+failure.getMessage());remove();}
    }
    private boolean expiresInBlock() {
        String block=BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos.containing(x,y,z)).getBlock()).toString();
        return BedrockParticleMotion.expiresInBlock(definition,block);
    }
    private void appearance(double time){appearance(time,false);}
    private void appearance(double time,boolean render) {
        var appearance=BedrockParticleMotion.appearance(definition,context,time,seconds,render);
        var size=appearance.size();
        dimensions=size;
        quadSize=(float)Math.max(0,Math.max(size[0],size[1]));
        var color=appearance.color();
        rCol=(float)Math.clamp(color[0],0,1);gCol=(float)Math.clamp(color[1],0,1);bCol=(float)Math.clamp(color[2],0,1);alpha=(float)Math.clamp(color[3],0,1);
        uv=appearance.uv();
    }
    @Override public void extract(QuadParticleRenderState state,Camera camera,float partial) {
        if(!definition.has("particle_appearance_billboard"))return;
        try {
            appearance((age+partial)/20d,true);
            if(removed||dimensions[0]<=0||dimensions[1]<=0||alpha<=0)return;
            // Authored scrolling UVs may extend beyond their image. Never sample a
            // neighbouring stitched sprite: crop the quad together with its UVs.
            double u0=Math.clamp(uv[0],0,1),u1=Math.clamp(uv[2],0,1);
            double v0=Math.clamp(uv[1],0,1),v1=Math.clamp(uv[3],0,1);
            if(u0==u1||v0==v1)return;
            var rotation=new org.joml.Quaternionf();getFacingCameraMode().setRotation(rotation,camera,partial);
            rotation.rotateZ(net.minecraft.util.Mth.lerp(partial,oRoll,roll));
            var eye=camera.position();
            var center=new Vector3f((float)(net.minecraft.util.Mth.lerp(partial,xo,x)-eye.x),
                (float)(net.minecraft.util.Mth.lerp(partial,yo,y)-eye.y),(float)(net.minecraft.util.Mth.lerp(partial,zo,z)-eye.z));
            var points=new java.util.ArrayList<Vector3f>();
            for(int i=0;i<4;i++) {
                double s=((i<2?u1:u0)-uv[0])/(uv[2]-uv[0]);
                double t=((i==0||i==3?v1:v0)-uv[1])/(uv[3]-uv[1]);
                points.add(new Vector3f((float)(dimensions[0]*(2*s-1)),
                    (float)(dimensions[1]*(1-2*t)),0).rotate(rotation).add(center));
            }
            ParticleRectangles.add(state,getLayer(),java.util.List.copyOf(points),sprite.getU((float)u0),sprite.getU((float)u1),sprite.getV((float)v0),sprite.getV((float)v1),
                net.minecraft.util.ARGB.colorFromFloat(alpha,rCol,gCol,bCol),getLightCoords(partial));
        }
        catch(RuntimeException failure){NativeEntityResources.runtimeError("Particle appearance "+definition.id+": "+failure.getMessage());}
    }
    private Vector3f worldVelocity(){var result=new Vector3f(velocity);if(localRotation)emitter.transform().transformDirection(result);return result;}
    @Override public FacingCameraMode getFacingCameraMode() {
        var facing=definition.component("particle_appearance_billboard").get("facing_camera_mode");
        String mode=facing==null?"lookat_xyz":facing.getAsString();
        return (rotation,camera,partial)-> {
            facingDirection=BedrockParticleMotion.direction(definition,context,worldVelocity(),facingDirection);
            var view=new Vector3f((float)(camera.position().x-net.minecraft.util.Mth.lerp(partial,xo,x)),
                (float)(camera.position().y-net.minecraft.util.Mth.lerp(partial,yo,y)),(float)(camera.position().z-net.minecraft.util.Mth.lerp(partial,zo,z)));
            rotation.set(BedrockParticleMotion.facing(mode,facingDirection,view,camera.rotation(),localRotation?emitter.transform():birth));
        };
    }
    @Override protected Layer getLayer(){return switch(BedrockParticleMotion.material(definition.material)){
        case ADDITIVE->ParticleLayers.ADDITIVE;case CUTOUT->Layer.OPAQUE;case BLEND->Layer.TRANSLUCENT;};}
    @Override protected int getLightCoords(float partial){return lit?super.getLightCoords(partial):net.minecraft.util.LightCoordsUtil.FULL_BRIGHT;}
    @Override protected float getU0(){return sprite.getU((float)uv[0]);}
    @Override protected float getU1(){return sprite.getU((float)uv[2]);}
    @Override protected float getV0(){return sprite.getV((float)uv[1]);}
    @Override protected float getV1(){return sprite.getV((float)uv[3]);}
    @Override public void remove() {
        if(removed)return;super.remove();emitter.count=Math.max(0,emitter.count-1);NativeParticles.released(definition.id);
        emitter.event(definition.component("particle_lifetime_events").get("expiration_event"),new Matrix4f().translation((float)x,(float)y,(float)z),worldVelocity());
    }
}
