package dev.matanyah.bedrockcompat.behavior;

import com.google.gson.*;
import java.util.*;

/** Complete, lossless definition graph. Subsystem implementations consume typed namespaces later. */
public final class BehaviorDefinitions {
    public record Definition(String pack,String path,String kind,String identifier,JsonObject body) {
        public Definition {body=body.deepCopy();}
        @Override public JsonObject body(){return body.deepCopy();}
    }
    public record Artifact(String pack,String path,byte[] bytes){public Artifact{bytes=bytes.clone();}@Override public byte[] bytes(){return bytes.clone();}}
    private final Map<String,Artifact> artifacts;
    private final Map<String,Map<String,Definition>> registries;
    private final List<String> deferred;
    public BehaviorDefinitions(List<BehaviorPackResources.Content> packs) {
        Map<String,Map<String,Definition>> result=new LinkedHashMap<>();List<String> pending=new ArrayList<>();Map<String,Artifact> raw=new LinkedHashMap<>();
        for(var pack:packs)for(var file:pack.files().entrySet()) {
            String path=file.getKey();raw.put(pack.pack().uuid()+"/"+path,new Artifact(pack.pack().uuid(),path,file.getValue()));
            if(path.endsWith(".mcfunction"))pending.add(pack.pack().displayName()+": command function deferred: "+path);
            if(path.startsWith("scripts/")&&path.endsWith(".js")){pending.add(pack.pack().displayName()+": script module deferred: "+path);continue;}
            if(!path.endsWith(".json")||path.equals("manifest.json")||path.startsWith("texts/"))continue;
            var json=JsonParser.parseString(new String(file.getValue(),java.nio.charset.StandardCharsets.UTF_8));
            if(!json.isJsonObject())continue;
            var root=json.getAsJsonObject();
            for(var entry:root.entrySet()) {
                String kind=entry.getKey();if(!entry.getValue().isJsonObject())continue;
                var body=entry.getValue().getAsJsonObject();
                if(Set.of("animations","animation_controllers","render_controllers").contains(kind)) {
                    for(var named:body.entrySet())if(named.getValue().isJsonObject())add(result,new Definition(pack.pack().uuid(),path,kind,named.getKey(),named.getValue().getAsJsonObject()));
                } else if(kind.startsWith("minecraft:")&&body.has("description")&&body.get("description").isJsonObject()&&body.getAsJsonObject("description").has("identifier")) {
                    String id=body.getAsJsonObject("description").get("identifier").getAsString();
                    if(net.minecraft.resources.Identifier.tryParse(id)==null)throw new IllegalArgumentException("Invalid Bedrock identifier "+id+" in "+path);
                    validateProperties(body);
                    add(result,new Definition(pack.pack().uuid(),path,kind,id,body));
                } else add(result,new Definition(pack.pack().uuid(),path,"document",path,root));
            }
            if(path.startsWith("entities/"))pending.add(pack.pack().displayName()+": gameplay components/events deferred: "+path);
        }
        Map<String,Map<String,Definition>> frozen=new LinkedHashMap<>();result.forEach((k,v)->frozen.put(k,Map.copyOf(v)));registries=Map.copyOf(frozen);deferred=List.copyOf(pending);artifacts=Map.copyOf(raw);
    }
    private static void add(Map<String,Map<String,Definition>> result,Definition d){result.computeIfAbsent(d.kind(),k->new LinkedHashMap<>()).put(d.identifier(),d);}
    public Map<String,Artifact> artifacts(){return artifacts;}
    public Map<String,Definition> registry(String kind){return registries.getOrDefault(kind,Map.of());}
    public Optional<Definition> find(String kind,String id){return Optional.ofNullable(registry(kind).get(id));}
    public List<String> deferred(){return deferred;}
    public JsonObject defaults(String id) {
        JsonObject values=new JsonObject();find("minecraft:entity",id).ifPresent(d->{var desc=d.body().getAsJsonObject("description");
            if(desc.has("properties"))desc.getAsJsonObject("properties").entrySet().forEach(e->values.add(e.getKey(),e.getValue().getAsJsonObject().get("default").deepCopy()));});return values;
    }
    public JsonObject clientProperties(String id,JsonObject values) {
        JsonObject visible=new JsonObject();find("minecraft:entity",id).ifPresent(d->{var desc=d.body().getAsJsonObject("description");
            if(desc.has("properties"))for(var e:desc.getAsJsonObject("properties").entrySet()) {
                var spec=e.getValue().getAsJsonObject();if(spec.has("client_sync")&&spec.get("client_sync").getAsBoolean()&&values.has(e.getKey()))visible.add(e.getKey(),values.get(e.getKey()).deepCopy());
            }});return visible;
    }
    public void validate(String id,String key,JsonElement value) {
        var d=find("minecraft:entity",id).orElseThrow(()->new IllegalArgumentException("Unknown actor "+id));
        var properties=d.body().getAsJsonObject("description").getAsJsonObject("properties");
        if(properties==null||!properties.has(key))throw new IllegalArgumentException("Unknown property "+key);
        validateValue(properties.getAsJsonObject(key),value);
    }
    private static void validateProperties(JsonObject body) {
        var desc=body.getAsJsonObject("description");if(desc==null||!desc.has("properties"))return;
        for(var e:desc.getAsJsonObject("properties").entrySet()) {var spec=e.getValue().getAsJsonObject();validateValue(spec,spec.get("default"));}
    }
    private static void validateValue(JsonObject spec,JsonElement v) {
        if(v==null||!v.isJsonPrimitive())throw new IllegalArgumentException("Property default/value must be primitive");var p=v.getAsJsonPrimitive();String type=spec.get("type").getAsString();
        boolean valid=switch(type) {
            case "bool"->p.isBoolean();
            case "enum"->p.isString()&&spec.getAsJsonArray("values").asList().contains(v);
            case "int","float"->p.isNumber()&&Double.isFinite(p.getAsDouble())&&(type.equals("float")||p.getAsDouble()==Math.rint(p.getAsDouble()))&&(!spec.has("range")||(p.getAsDouble()>=spec.getAsJsonArray("range").get(0).getAsDouble()&&p.getAsDouble()<=spec.getAsJsonArray("range").get(1).getAsDouble()));
            default->false;
        };if(!valid)throw new IllegalArgumentException("Invalid "+type+" property value: "+v);
    }
}
