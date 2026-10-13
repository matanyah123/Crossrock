package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.entity.Molang;
import java.util.*;

/** Actual Java states supplied by the client or an integration; never creates a state or block. */
public final class BedrockBlockStates {
    public record Variant(String selector,Map<String,Object> values) {
        public Variant { values=Map.copyOf(values); }
    }
    private BedrockBlockStates() {}
    public static Map<String,Object> map(Variant variant,JsonObject declared) {
        Map<String,Object> result=new LinkedHashMap<>(variant.values());
        variant.values().forEach((k,v)->result.putIfAbsent("minecraft:"+k,v));
        if(variant.values().containsKey("facing"))result.put("minecraft:cardinal_direction",variant.values().get("facing"));
        if(declared!=null)for(var entry:declared.entrySet()) {
            String key=entry.getKey(),local=key.substring(key.indexOf(':')+1);
            Object value=result.get(key);if(value==null)value=variant.values().get(local);
            if(value==null)throw new IllegalArgumentException("No Java state property for "+key);
            JsonElement domain=entry.getValue();boolean allowed=false;
            if(domain.isJsonArray())for(var candidate:domain.getAsJsonArray())allowed|=Objects.equals(primitive(candidate),value);
            else if(domain.isJsonObject()&&value instanceof Number n) {
                var range=domain.getAsJsonObject().getAsJsonObject("values");
                if(range==null)range=domain.getAsJsonObject();
                allowed=n.doubleValue()>=range.get("min").getAsDouble()&&n.doubleValue()<=range.get("max").getAsDouble();
            }
            if(!allowed)throw new IllegalArgumentException("Java state outside Bedrock domain: "+key+"="+value);
            result.put(key,value);
        }
        return Map.copyOf(result);
    }
    public static Object evaluate(JsonElement expression,Map<String,Object> states) {
        if(expression.isJsonPrimitive()&&!expression.getAsJsonPrimitive().isString())return expression.getAsJsonPrimitive().isBoolean()?(expression.getAsBoolean()?1d:0d):expression.getAsDouble();
        if(expression.isJsonObject())expression=expression.getAsJsonObject().get("expression");
        String source=expression.getAsString();
        String code=source.replaceAll("'[^']*'|\"[^\"]*\"", "").toLowerCase(Locale.ROOT);
        if(code.matches("(?s).*(?:\\b(?:v|variable|t|temp|c|context)\\.|math\\.(?:random|die_roll)|loop\\s*\\(|for_each\\s*\\().*"))
            throw new IllegalArgumentException("Permutation expression requires unavailable runtime context");
        var context=new Molang.Context((name,args)->{
            if(!Set.of("block_state","block_property","has_block_state").contains(name)||args.size()!=1)
                throw new IllegalArgumentException("Unavailable block query: "+name);
            String key=args.getFirst().toString();
            if(name.equals("has_block_state"))return states.containsKey(key)?1d:0d;
            if(!states.containsKey(key))throw new IllegalArgumentException("Unmapped block state: "+key);
            Object value=states.get(key);return value instanceof Boolean b?(b?1d:0d):value;
        });
        return Molang.compile(source).evaluate(context);
    }
    public static JsonObject components(JsonObject block,Map<String,Object> states) {
        JsonObject result=block.getAsJsonObject("components").deepCopy();
        if(block.has("permutations"))for(var p:block.getAsJsonArray("permutations")) {
            var permutation=p.getAsJsonObject();
            if(Molang.truth(evaluate(permutation.get("condition"),states)))
                permutation.getAsJsonObject("components").entrySet().forEach(e->result.add(e.getKey(),e.getValue().deepCopy()));
        }
        for(String key:List.of("minecraft:geometry","minecraft:item_visual")) {
            JsonObject geometry=key.equals("minecraft:geometry")?object(result,key):object(object(result,key),"geometry");
            if(geometry!=null&&geometry.has("bone_visibility"))for(var e:new ArrayList<>(geometry.getAsJsonObject("bone_visibility").entrySet()))
                geometry.getAsJsonObject("bone_visibility").addProperty(e.getKey(),Molang.truth(evaluate(e.getValue(),states)));
        }
        return result;
    }
    private static JsonObject object(JsonObject o,String k){return o!=null&&o.has(k)&&o.get(k).isJsonObject()?o.getAsJsonObject(k):null;}
    private static Object primitive(JsonElement e){var p=e.getAsJsonPrimitive();return p.isBoolean()?p.getAsBoolean():p.isNumber()?p.getAsDouble():p.getAsString();}
}
