package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;

/** Resolves legacy geometry inheritance against the winning reload generation, never file order. */
public final class BedrockGeometryResolver {
    private final Map<String,JsonObject> sources;
    private final Map<String,JsonObject> resolved=new LinkedHashMap<>();
    private final Map<String,String> failed=new HashMap<>();
    private final Map<String,Integer> depths=new HashMap<>();
    private static final class DepthLimit extends IllegalArgumentException {
        DepthLimit(){super("Geometry inheritance exceeds 64 levels");}
    }
    private BedrockGeometryResolver(Map<String,JsonObject> sources){this.sources=sources;}

    public static Map<String,BedrockGeometry> resolve(Map<String,JsonObject> sources,Consumer<String> errors) {
        var resolver=new BedrockGeometryResolver(sources);
        Map<String,BedrockGeometry> models=new LinkedHashMap<>();
        for(String id:sources.keySet())try {
            models.put(id,BedrockGeometry.parse(resolver.document(id,new LinkedHashSet<>())));
        }catch(RuntimeException ex){errors.accept(id+": "+ex.getMessage());}
        return Map.copyOf(models);
    }

    private JsonObject document(String id,Set<String> path) {
        if(resolved.containsKey(id))return resolved.get(id);
        if(failed.containsKey(id))throw new IllegalArgumentException(failed.get(id));
        JsonObject source=sources.get(id);
        if(source==null)throw new IllegalArgumentException("Missing geometry parent: "+id);
        if(path.size()>=64)throw new DepthLimit();
        if(!path.add(id))throw new IllegalArgumentException("Cyclic geometry inheritance: "+id);
        try {
            JsonObject local=normalized(source),base=new JsonObject();int depth=1;
            if(local.has("bedrock_parent")) {
                String parent=local.get("bedrock_parent").getAsString();
                if(parent.isBlank())throw new IllegalArgumentException("Empty geometry parent");
                base=document(parent,path).deepCopy();
                depth=depths.get(parent)+1;
                if(depth>64)throw new DepthLimit();
            }
            JsonObject result=base.deepCopy();
            for(var property:local.entrySet()) {
                String key=property.getKey();
                if(key.equals("bones")||key.equals("bedrock_parent"))continue;
                if(key.equals("description")) {
                    JsonObject description=result.has(key)?result.getAsJsonObject(key).deepCopy():new JsonObject();
                    property.getValue().getAsJsonObject().entrySet().forEach(e->description.add(e.getKey(),e.getValue().deepCopy()));
                    result.add(key,description);
                }else result.add(key,property.getValue().deepCopy());
            }
            Map<String,JsonObject> bones=new LinkedHashMap<>();
            readBones(base).forEach((name,bone)->bones.put(name,bone.deepCopy()));
            readBones(local).forEach((name,bone)-> {
                JsonObject merged=bones.getOrDefault(name,new JsonObject()).deepCopy();
                // Absent fields inherit; explicit arrays (including []) replace parent arrays.
                bone.entrySet().forEach(e->merged.add(e.getKey(),e.getValue().deepCopy()));
                bones.put(name,merged);
            });
            if(bones.size()>4096)throw new IllegalArgumentException("Too many inherited bones");
            JsonArray entries=new JsonArray();bones.values().forEach(entries::add);result.add("bones",entries);
            // Validate every resolved parent too. A malformed ancestor must not be silently repaired
            // into a different model by a descendant, and inherited aggregate budgets still apply.
            BedrockGeometry.parse(result);
            resolved.put(id,result);
            depths.put(id,depth);
            return result;
        }catch(RuntimeException ex){
            // A traversal limit can be caused by descendants above an otherwise valid ancestor.
            // Do not poison that ancestor's cache merely because it was reached at depth 64.
            if(!(ex instanceof DepthLimit))failed.put(id,ex.getMessage());
            throw ex;
        }
        finally{path.remove(id);}
    }

    private static Map<String,JsonObject> readBones(JsonObject source) {
        Map<String,JsonObject> result=new LinkedHashMap<>();
        if(!source.has("bones"))return result;
        JsonArray bones=source.getAsJsonArray("bones");
        if(bones.size()>4096)throw new IllegalArgumentException("Too many bones");
        for(JsonElement entry:bones) {
            JsonObject bone=entry.getAsJsonObject();String name=bone.get("name").getAsString();
            if(name.isEmpty()||result.putIfAbsent(name,bone)!=null)
                throw new IllegalArgumentException("Empty or duplicate bone: "+name);
        }
        return result;
    }

    private static JsonObject normalized(JsonObject source) {
        JsonObject result=source.deepCopy();
        // Keep legacy and modern dimension spellings comparable across inheritance boundaries.
        JsonObject description=source.has("description")?source.getAsJsonObject("description").deepCopy():new JsonObject();
        JsonObject dimensions=source.has("description")?description:source;
        for(String axis:List.of("width","height")) {
            String modern="texture_"+axis,legacy="texture"+axis;
            if(dimensions.has(modern))description.add(modern,dimensions.get(modern).deepCopy());
            else if(dimensions.has(legacy))description.add(modern,dimensions.get(legacy).deepCopy());
            description.remove(legacy);
        }
        result.add("description",description);
        return result;
    }
}
