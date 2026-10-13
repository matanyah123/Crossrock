package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import org.joml.*;
import java.lang.Math;

/** Numerical particle contracts shared by the real client and headless conformance tests. */
public final class BedrockParticleMotion {
    private BedrockParticleMotion() {}
    public enum Material { CUTOUT, BLEND, ADDITIVE }
    public static Material material(String name) {
        return switch(name){case "particles_alpha","particles_opaque"->Material.CUTOUT;
            case "particles_add"->Material.ADDITIVE;default->Material.BLEND;};
    }
    public record Appearance(double[] size,double[] color,double[] uv,boolean lit) {}
    public static Appearance appearance(BedrockParticleEffect d,Molang.Context c,double age,double lifetime,boolean render) {
        c.setVariable("particle_age",age);d.curves(c);
        if(render)d.script(d.component("particle_initialization"),"per_render_expression",c);
        return new Appearance(d.vector(d.component("particle_appearance_billboard").get("size"),c,.1,.1),
            d.color(d.component("particle_appearance_tinting").get("color"),c),d.uv(c,age,lifetime),d.has("particle_appearance_lighting"));
    }
    public static double lifetime(BedrockParticleEffect d,Molang.Context c){return Math.max(0,d.value(d.component("particle_lifetime_expression"),"max_lifetime",c,1));}
    public static boolean expired(BedrockParticleEffect d,Molang.Context c,double age,double lifetime) {
        c.setVariable("particle_age",age);
        return age>=lifetime||d.value(d.component("particle_lifetime_expression"),"expiration_expression",c,0)!=0;
    }
    public static double initialSpin(BedrockParticleEffect d,Molang.Context c){return d.value(d.component("particle_initial_spin"),"rotation",c,0);}
    public static double initialSpinRate(BedrockParticleEffect d,Molang.Context c){return d.value(d.component("particle_initial_spin"),"rotation_rate",c,0);}
    public static Vector3f relativePosition(BedrockParticleEffect d,Molang.Context c,Matrix4f frame) {
        var p=d.vector(d.component("particle_motion_parametric").get("relative_position"),c,0,0,0);
        return frame.transformPosition(new Vector3f((float)p[0],(float)p[1],(float)p[2]));
    }
    public static Vector3f initialVelocity(BedrockParticleEffect d,Molang.Context c,Vector3f direction,
            Matrix4f birth,boolean localRotation,Vector3f supplied,boolean vanilla,boolean inherited) {
        var v=new Vector3f(direction);var speed=d.raw("particle_initial_speed");
        if(speed!=null&&speed.isJsonArray()){var a=d.vector(speed,c,0,0,0);v.set((float)a[0],(float)a[1],(float)a[2]);}
        else v.mul((float)d.number(speed,c,0));
        if(!localRotation)birth.transformDirection(v);
        if(inherited) {
            var extra=new Vector3f(supplied);
            if(localRotation)new Matrix4f(birth).invert().transformDirection(extra);
            v.add(extra);
        } else if(vanilla&&speed==null)v.set(supplied).mul(20);
        return v;
    }
    public static void dynamics(BedrockParticleEffect d,Molang.Context c,Vector3f velocity,Matrix4f birth,boolean localRotation,double dt) {
        var component=d.component("particle_motion_dynamic");var a=d.vector(component.get("linear_acceleration"),c,0,0,0);
        var acceleration=new Vector3f((float)a[0],(float)a[1],(float)a[2]);
        if(!localRotation)birth.transformDirection(acceleration);
        float drag=(float)Math.max(0,d.value(component,"linear_drag_coefficient",c,0));
        // Both terms use the old velocity (not drag applied to the newly accelerated velocity).
        velocity.mul((float)Math.max(0,1-drag*dt)).add(acceleration.mul((float)dt));
    }
    public static double spin(BedrockParticleEffect d,Molang.Context c,double speed,double dt) {
        var component=d.component("particle_motion_dynamic");
        return speed+(d.value(component,"rotation_acceleration",c,0)-speed*d.value(component,"rotation_drag_coefficient",c,0))*dt;
    }
    public static boolean expiresInBlock(BedrockParticleEffect d,String block) {
        for(String name:new String[]{"particle_expire_if_in_blocks","particle_expire_if_not_in_blocks"}) {
            var values=d.raw(name);if(values==null||!values.isJsonArray())continue;
            boolean match=values.getAsJsonArray().asList().stream().anyMatch(v->canonical(v.getAsString()).equals(canonical(block)));
            if(match==name.equals("particle_expire_if_in_blocks"))return true;
        }return false;
    }
    private static String canonical(String id){return id.contains(":")?id:"minecraft:"+id;}
    public static double planeDistance(BedrockParticleEffect d,Molang.Context c,Vector3f point,Vector3f emitterPosition) {
        var p=d.vector(d.raw("particle_kill_plane"),c,0,1,0,0);
        return p[0]*(point.x-emitterPosition.x)+p[1]*(point.y-emitterPosition.y)+p[2]*(point.z-emitterPosition.z)+p[3];
    }
    public static boolean crossesPlane(double previous,double now){return previous*now<0||previous!=0&&now==0;}
    public static void collisionVelocity(Vector3f velocity,boolean x,boolean y,boolean z,double bounce,double drag,double dt) {
        if(x)velocity.x*=-bounce;if(y)velocity.y*=-bounce;if(z)velocity.z*=-bounce;
        // Collision drag is a speed decrement in blocks/sec, not a multiplicative drag coefficient.
        float speed=velocity.length();if(speed>0)velocity.mul((float)Math.max(0,speed-Math.max(0,drag)*dt)/speed);
    }
    public static JsonArray collisionEvents(JsonObject collision) {
        var result=new JsonArray();var events=collision.get("events");
        if(events!=null){if(events.isJsonArray())return events.getAsJsonArray();result.add(events);}return result;
    }
    public static Vector3f direction(BedrockParticleEffect d,Molang.Context c,Vector3f velocity,Vector3f previous) {
        var parametric=d.component("particle_motion_parametric");
        var billboard=d.component("particle_appearance_billboard").getAsJsonObject("direction");
        JsonElement custom=parametric.get("direction");
        if(custom==null&&billboard!=null&&billboard.has("mode")&&billboard.get("mode").getAsString().equals("custom_direction"))custom=billboard.get("custom_direction");
        if(custom!=null){var a=d.vector(custom,c,0,1,0);return new Vector3f((float)a[0],(float)a[1],(float)a[2]);}
        double threshold=billboard==null?.01:d.value(billboard,"min_speed_threshold",c,.01);
        return velocity.lengthSquared()>=threshold*threshold?new Vector3f(velocity):new Vector3f(previous);
    }
    public static Quaternionf facing(String mode,Vector3f direction,Vector3f view,Quaternionf camera,Matrix4f emitter) {
        if(mode.equals("rotate_xyz"))return new Quaternionf(camera);
        if(mode.startsWith("emitter_transform_")) {
            // A reflected model-to-world matrix is not a quaternion. Build a right-handed basis from the authored plane.
            var x=emitter.transformDirection(new Vector3f(mode.endsWith("yz")?0:1,mode.endsWith("yz")?1:0,0));
            var y=emitter.transformDirection(mode.endsWith("xy")?new Vector3f(0,1,0):new Vector3f(0,0,1));
            return basis(x,y);
        }
        if(mode.equals("rotate_y")) {
            var normal=new Vector3f(0,0,1).rotate(camera);normal.y=0;
            return basis(new Vector3f(0,1,0).cross(normal),new Vector3f(0,1,0));
        }
        if(mode.equals("lookat_y")){var normal=new Vector3f(view);normal.y=0;return basis(new Vector3f(0,1,0).cross(normal),new Vector3f(0,1,0));}
        if(mode.equals("lookat_xyz")) {
            var normal=safe(view,new Vector3f(0,0,1));var x=new Vector3f(0,1,0).cross(normal);
            if(x.lengthSquared()<1e-9)x.set(1,0,0);
            return basis(x,new Vector3f(normal).cross(x));
        }
        if(mode.equals("lookat_direction"))return BedrockParticleEffect.trailRotation(direction,view);
        var d=safe(direction,new Vector3f(0,1,0));var up=new Vector3f(0,1,0);
        if(Math.abs(d.dot(up))>.999)up.set(1,0,0);
        if(mode.equals("direction_x"))return basis(d,new Vector3f(up).sub(new Vector3f(d).mul(up.dot(d))));
        if(mode.equals("direction_y"))return basis(new Vector3f(up).sub(new Vector3f(d).mul(up.dot(d))),d);
        if(mode.equals("direction_z"))return basis(new Vector3f(up).cross(d),new Vector3f(d).cross(new Vector3f(up).cross(d)));
        throw new IllegalArgumentException("Unsupported particle facing mode: "+mode);
    }
    private static Vector3f safe(Vector3f v,Vector3f fallback){return v.isFinite()&&v.lengthSquared()>1e-12?new Vector3f(v).normalize():new Vector3f(fallback);}
    private static Quaternionf basis(Vector3f x,Vector3f y) {
        x=safe(x,new Vector3f(1,0,0));y=new Vector3f(y).sub(new Vector3f(x).mul(x.dot(y)));
        if(y.lengthSquared()<1e-9)y=(Math.abs(x.y)<.9?new Vector3f(0,1,0):new Vector3f(0,0,1)).sub(new Vector3f(x).mul(Math.abs(x.y)<.9?x.y:x.z));
        y.normalize();return new Quaternionf().setFromNormalized(new Matrix3f(x,y,new Vector3f(x).cross(y))).normalize();
    }
}
