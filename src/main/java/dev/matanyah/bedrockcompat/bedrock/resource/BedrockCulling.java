package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.entity.BedrockGeometryMesh;
import java.util.*;

/** Value-only authored geometry-part culling rules; world-neighbor evaluation lives in the baked renderer. */
public final class BedrockCulling {
    private static final Set<String> DIRECTIONS=Set.of("north","south","east","west","up","down");
    private BedrockCulling() {}
    public static JsonArray rules(JsonObject source) {
        JsonArray result=new JsonArray();
        for(var element:source.getAsJsonArray("rules")) {
            JsonObject r=element.getAsJsonObject();
            for(String key:r.keySet())if(!Set.of("geometry_part","direction","condition","cull_against_full_and_opaque").contains(key))throw new IllegalArgumentException("Unknown culling field "+key);
            if(r.has("cull_against_full_and_opaque")&&(!r.get("cull_against_full_and_opaque").isJsonPrimitive()||!r.getAsJsonPrimitive("cull_against_full_and_opaque").isBoolean()))throw new IllegalArgumentException("Culling full flag must be boolean");
            String direction=r.get("direction").getAsString();if(!DIRECTIONS.contains(direction))throw new IllegalArgumentException("Invalid culling direction");
            String condition=r.has("condition")?r.get("condition").getAsString():"default";
            if(!Set.of("default","same_block","same_block_permutation","same_culling_layer").contains(condition))throw new IllegalArgumentException("Invalid culling condition");
            JsonObject part=r.getAsJsonObject("geometry_part");if(part==null||!part.has("bone"))throw new IllegalArgumentException("Culling requires a bone");
            for(String key:part.keySet())if(!Set.of("bone","cube","face").contains(key))throw new IllegalArgumentException("Unknown culling part "+key);
            if(part.has("cube")&&(part.get("cube").getAsDouble()!=part.get("cube").getAsInt()||part.get("cube").getAsInt()<0))throw new IllegalArgumentException("Invalid culling cube index");
            if(part.has("face")&&(!part.has("cube")||!DIRECTIONS.contains(part.get("face").getAsString())))throw new IllegalArgumentException("Culling face requires a cube and valid face");
            result.add(r.deepCopy());
        }
        return result;
    }
    public static JsonArray forQuad(JsonArray rules,BedrockGeometryMesh.Quad q) {
        JsonArray result=new JsonArray();
        for(var element:rules) {
            JsonObject r=element.getAsJsonObject(),part=r.getAsJsonObject("geometry_part");
            if(!part.get("bone").getAsString().equals(q.bone()))continue;
            if(part.has("cube")&&part.get("cube").getAsInt()!=q.cubeIndex())continue;
            if(part.has("face")&&!part.get("face").getAsString().equals(q.face()))continue;
            JsonObject baked=new JsonObject();baked.add("direction",r.get("direction"));
            baked.addProperty("condition",r.has("condition")?r.get("condition").getAsString():"default");
            baked.addProperty("full",!r.has("cull_against_full_and_opaque")||r.get("cull_against_full_and_opaque").getAsBoolean());result.add(baked);
        }
        return result;
    }
}
