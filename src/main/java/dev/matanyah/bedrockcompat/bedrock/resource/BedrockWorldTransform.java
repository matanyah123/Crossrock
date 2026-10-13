package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import org.joml.*;
import java.lang.Math;
import java.util.*;

/** World transform uses block units around block center; native mesh uses sixteenths. */
public final class BedrockWorldTransform {
    private BedrockWorldTransform() {}
    public static Matrix4f matrix(JsonObject t) {
        for(String key:t.keySet())if(!Set.of("translation","rotation","scale","rotation_pivot","scale_pivot").contains(key))
            throw new IllegalArgumentException("Unknown world transform field: "+key);
        Vector3f translation=vector(t,"translation",new Vector3f()).mul(16),rotation=vector(t,"rotation",new Vector3f());
        Vector3f scale=vector(t,"scale",new Vector3f(1)),rp=vector(t,"rotation_pivot",new Vector3f()).mul(16),sp=vector(t,"scale_pivot",new Vector3f()).mul(16);
        for(float angle:new float[]{rotation.x,rotation.y,rotation.z})if(angle%90!=0)throw new IllegalArgumentException("Block rotation must use 90 degree increments");
        return new Matrix4f().translate(translation).translate(rp)
            .rotateZYX((float)Math.toRadians(-rotation.z),(float)Math.toRadians(rotation.y),(float)Math.toRadians(-rotation.x))
            .translate(-rp.x,-rp.y,-rp.z).translate(sp).scale(scale).translate(-sp.x,-sp.y,-sp.z);
    }
    private static Vector3f vector(JsonObject o,String key,Vector3f fallback) {
        if(!o.has(key))return fallback;
        JsonElement value=o.get(key);
        if(value.isJsonObject()) {
            JsonObject v=value.getAsJsonObject();JsonArray array=new JsonArray();
            for(String axis:List.of("x","y","z"))array.add(v.has(axis)?v.get(axis):new JsonPrimitive(key.equals("scale")?1:0));
            value=array;
        }
        JsonObject wrapper=new JsonObject();wrapper.add(key,value);
        return BedrockItemDisplayTransforms.vector(wrapper,key,fallback,-Float.MAX_VALUE,Float.MAX_VALUE);
    }
}
