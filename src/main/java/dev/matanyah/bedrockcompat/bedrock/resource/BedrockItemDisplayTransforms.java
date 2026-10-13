package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import org.joml.*;
import java.lang.Math;
import java.util.*;

/** Bedrock model-unit transforms. Separate left contexts are never mirrored by Java. */
public final class BedrockItemDisplayTransforms {
    public static final List<String> CONTEXTS = List.of("gui","firstperson_righthand","firstperson_lefthand",
        "thirdperson_righthand","thirdperson_lefthand","ground","fixed","head");
    private BedrockItemDisplayTransforms() {}
    public static Matrix4f matrix(JsonObject transform, String context) {
        if (!CONTEXTS.contains(context)) throw new IllegalArgumentException("Unsupported display context: " + context);
        for (String key : transform.keySet()) if (!Set.of("translation","rotation","scale","rotation_pivot","scale_pivot","fit_to_frame").contains(key))
            throw new IllegalArgumentException("Unsupported display field: " + key);
        if(transform.has("fit_to_frame")&&(!transform.get("fit_to_frame").isJsonPrimitive()||!transform.getAsJsonPrimitive("fit_to_frame").isBoolean()))
            throw new IllegalArgumentException("fit_to_frame must be boolean");
        Vector3f defaultRotation = switch(context) {
            case "gui" -> new Vector3f(30,45,0);
            case "firstperson_righthand" -> new Vector3f(0,45,0);
            case "firstperson_lefthand" -> new Vector3f(0,-135,0);
            case "thirdperson_righthand","thirdperson_lefthand" -> new Vector3f(75,45,0);
            default -> new Vector3f();
        };
        float defaultScale = switch(context) {case "gui"->.625f;case "firstperson_righthand","firstperson_lefthand"->.4f;
            case "thirdperson_righthand","thirdperson_lefthand"->.375f;case "ground"->.25f;case "fixed"->.5f;default->1;};
        Vector3f t=vector(transform,"translation",new Vector3f(0,context.equals("ground")?3:context.startsWith("thirdperson")?2.5f:0,0),-80,80);
        Vector3f r=vector(transform,"rotation",defaultRotation,-360,360);
        Vector3f s=vector(transform,"scale",new Vector3f(defaultScale),0,4);
        Vector3f rp=vector(transform,"rotation_pivot",new Vector3f(),-80,80),sp=vector(transform,"scale_pivot",new Vector3f(),-80,80);
        return new Matrix4f().translate(t).translate(rp)
            .rotateZYX((float)Math.toRadians(-r.z),(float)Math.toRadians(r.y),(float)Math.toRadians(-r.x))
            .translate(-rp.x,-rp.y,-rp.z).translate(sp).scale(s).translate(-sp.x,-sp.y,-sp.z);
    }
    public static Matrix4f fitToFrame(Matrix4f authored,List<Vector3f> points,Vector3f translation) {
        Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY);
        Matrix4f noTranslation=new Matrix4f().translation(new Vector3f(translation).negate()).mul(authored);
        for(var point:points){Vector3f p=noTranslation.transformPosition(new Vector3f(point));min.min(p);max.max(p);}
        float extent=Math.max(max.x-min.x,max.y-min.y);
        if(!Float.isFinite(extent)||extent<1e-7f)return authored;
        Vector3f center=new Vector3f(min).add(max).mul(.5f);
        return new Matrix4f().translation(translation).scale(16/extent).translate(center.negate()).mul(noTranslation);
    }
    static Vector3f vector(JsonObject o,String key,Vector3f fallback,float min,float max) {
        if(!o.has(key))return fallback;
        JsonArray a=o.getAsJsonArray(key);if(a.size()!=3)throw new IllegalArgumentException("Expected 3 values for "+key);
        float[] v=new float[3];for(int i=0;i<3;i++) {
            if(!a.get(i).isJsonPrimitive()||!a.get(i).getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Non-numeric "+key);
            v[i]=a.get(i).getAsFloat();if(!Float.isFinite(v[i])||v[i]<min||v[i]>max)throw new IllegalArgumentException("Out of range "+key);
        }
        return new Vector3f(v[0],v[1],v[2]);
    }
}
