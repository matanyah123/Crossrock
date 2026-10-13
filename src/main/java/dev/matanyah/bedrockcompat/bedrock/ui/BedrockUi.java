package dev.matanyah.bedrockcompat.bedrock.ui;

import com.google.gson.*;
import java.util.*;

/** Bounded JSON UI definition compiler. Unknown controls never become pretend Java widgets. */
public final class BedrockUi {
    public record Source(String file,JsonObject document) {}
    public record Node(String name,String namespace,Map<String,JsonElement> properties,List<Node> children) {}
    public record Animation(String property,double duration,JsonElement from,JsonElement to,String easing,String next) {}
    public record Program(Map<String,Node> roots,Map<String,Animation> animations,List<String> errors,Map<String,String> rootFailures) {
        public Program(Map<String,Node> roots,Map<String,Animation> animations,List<String> errors){this(roots,animations,errors,Map.of());}
        public static final Program EMPTY=new Program(Map.of(),Map.of(),List.of());
    }
    private record Definition(String file,String namespace,String key,JsonObject value) {}
    private final Map<String,Definition> definitions=new LinkedHashMap<>();
    private final Map<String,JsonElement> globals=new LinkedHashMap<>();
    private final List<String> errors=new ArrayList<>();
    private int count;
    private static final Set<String> TYPES=Set.of("panel","screen","image","label","stack_panel","button","grid","custom");
    private static final Set<String> FIELDS=Set.of("type","controls","size","offset","anchor_from","anchor_to","alpha","propagate_alpha","color","layer","visible","ignored","enabled","text","text_alignment","shadow","texture","uv","uv_size","clips_children","orientation","bindings","anims","variables","min_size","max_size","button_mappings","focus_enabled","consume_event","default_control","hover_control","pressed_control","collection_name","grid_dimensions","grid_item_template","renderer","nineslice_size","nine_slice_left","nine_slice_right","nine_slice_top","nine_slice_buttom");

    public static Program compile(List<Source> sources) {
        var compiler=new BedrockUi();
        BedrockUiTemplates.primitives().forEach(compiler::add);
        for(var source:sources)try{compiler.add(source);}catch(RuntimeException e){compiler.error(source.file,e);}
        var roots=new LinkedHashMap<String,Node>();var animations=new LinkedHashMap<String,Animation>();var rootFailures=new LinkedHashMap<String,String>();
        for(var entry:compiler.definitions.entrySet())try {
            var d=entry.getValue();
            compiler.count=0; // Each independent root gets its own bounded expansion budget.
            if(d.file.equals("<compatibility primitive>"))continue;
            if(d.value.has("anim_type")) {
                String type=d.value.get("anim_type").getAsString();
                if(!Set.of("alpha","offset","size","color").contains(type))throw new IllegalArgumentException("Unsupported animation type "+type);
                String easing=d.value.has("easing")?d.value.get("easing").getAsString():"linear";
                if(!Set.of("linear","in_quad","out_quad","in_out_quad").contains(easing))throw new IllegalArgumentException("Unsupported easing "+easing);
                for(String field:d.value.keySet())if(!Set.of("anim_type","duration","from","to","easing","next").contains(field))throw new IllegalArgumentException("Unsupported animation field "+field);
                double duration=d.value.get("duration").getAsDouble();
                if(!Double.isFinite(duration)||duration<=0)throw new IllegalArgumentException("Animation duration must be finite and positive");
                animations.put(entry.getKey(),new Animation(type,duration,d.value.get("from"),d.value.get("to"),easing,d.value.has("next")?qualify(d.namespace,d.value.get("next").getAsString().replaceFirst("^@","")):""));
            }else roots.put(entry.getKey(),compiler.node(d.key,d.namespace,d.value,Map.of(),new LinkedHashSet<>(),0));
        }catch(RuntimeException e){String location=entry.getValue().file+"#/"+entry.getValue().key.replace("~","~0").replace("/","~1");rootFailures.put(entry.getKey(),location+": "+e.getMessage());compiler.error(location,e);}
        return new Program(Map.copyOf(roots),Map.copyOf(animations),List.copyOf(compiler.errors),Map.copyOf(rootFailures));
    }
    private void add(Source source) {
        var doc=source.document;
        validateTree(doc);
        if(source.file.endsWith("_global_variables.json")){doc.entrySet().stream().filter(e->e.getKey().startsWith("$")).forEach(e->globals.put(e.getKey(),e.getValue().deepCopy()));return;}
        String namespace=BedrockUiTemplates.namespace(source);
        if(!namespace.matches("[a-zA-Z0-9_-]+"))throw new IllegalArgumentException("Invalid namespace");
        for(var entry:doc.entrySet())if(!entry.getKey().equals("namespace")) {
            if(definitions.size()>=4096)throw new IllegalArgumentException("Definition limit exceeded");
            definitions.put(qualify(namespace,entry.getKey().split("@",2)[0]),new Definition(source.file,namespace,entry.getKey(),entry.getValue().getAsJsonObject().deepCopy()));
        }
    }
    private Node node(String key,String ns,JsonObject authored,Map<String,JsonElement> inherited,Set<String> path,int depth) {
        if(depth>48||++count>16384)throw new IllegalArgumentException("UI tree resource limit exceeded");
        JsonObject value=resolve(key,ns,authored,path,depth);
        var scope=new LinkedHashMap<String,JsonElement>(globals);scope.putAll(inherited);
        for(var e:value.entrySet())if(e.getKey().startsWith("$")) {
            String name=e.getKey().replace("|default","");
            if(e.getKey().endsWith("|default"))scope.putIfAbsent(name,e.getValue());else scope.put(name,e.getValue());
        }
        if(value.has("variables")) {
            var blocks=value.get("variables");var list=blocks.isJsonArray()?blocks.getAsJsonArray():new JsonArray();if(!blocks.isJsonArray())list.add(blocks);
            for(var block:list){var vars=block.getAsJsonObject();var requires=substitute(vars.get("requires"),scope,new HashSet<>(),0);
                if(requires.isJsonPrimitive()&&requires.getAsJsonPrimitive().isString())requires=BedrockUiExpression.evaluate(requires.getAsString(),name->substitute(scope.get(name),scope,new HashSet<>(),0));
                if(BedrockUiExpression.bool(requires))for(var e:vars.entrySet())if(e.getKey().startsWith("$"))scope.put(e.getKey(),e.getValue());else if(!e.getKey().equals("requires"))throw new IllegalArgumentException("Unsupported conditional variable field "+e.getKey());
            }
        }
        var properties=new LinkedHashMap<String,JsonElement>();
        for(var e:value.entrySet())if(!e.getKey().startsWith("$")&&!Set.of("controls","variables").contains(e.getKey())) {
            if(!FIELDS.contains(e.getKey()))throw new IllegalArgumentException("Unsupported control field "+e.getKey());
            properties.put(e.getKey(),substitute(e.getValue(),scope,new HashSet<>(),0));
        }
        String type=properties.getOrDefault("type",new JsonPrimitive("panel")).getAsString();
        if(!TYPES.contains(type))throw new IllegalArgumentException("Unsupported control type "+type);
        if(type.equals("custom")&&!properties.getOrDefault("renderer",new JsonPrimitive("")).getAsString().equals("inventory_item_renderer"))throw new IllegalArgumentException("Unsupported custom renderer");
        if(type.equals("button"))BedrockUiInteraction.mappings(properties);
        if(!type.equals("button")&&properties.containsKey("button_mappings"))throw new IllegalArgumentException("Screen/global input mappings unsupported");
        var children=new ArrayList<Node>();
        if(value.has("controls"))for(var child:controlArray(value.get("controls"),scope).getAsJsonArray())for(var e:child.getAsJsonObject().entrySet())
            children.add(node(e.getKey(),ns,e.getValue().getAsJsonObject(),scope,path,depth+1));
        if(type.equals("grid")) {
            for(String required:List.of("grid_item_template","grid_dimensions","collection_name"))if(!properties.containsKey(required))throw new IllegalArgumentException("Grid missing "+required);
            String template=qualify(ns,properties.get("grid_item_template").getAsString());
            var definition=definitions.get(template);
            if(definition==null||!path.add(template))throw new IllegalArgumentException("Missing/cyclic grid template: "+template);
            children.add(node(definition.key,definition.namespace,definition.value,scope,path,depth+1));path.remove(template);
        }
        return new Node(key.split("@",2)[0],ns,Map.copyOf(properties),List.copyOf(children));
    }
    private static JsonElement controlArray(JsonElement value,Map<String,JsonElement> scope){
        var seen=new HashSet<String>();while(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()&&value.getAsString().startsWith("$")){
            String name=value.getAsString();if(!seen.add(name))throw new IllegalArgumentException("Control variable cycle: "+name);value=scope.get(name);if(value==null)throw new IllegalArgumentException("Missing control variable: "+name);
        }return value;
    }
    private JsonObject resolve(String key,String ns,JsonObject authored,Set<String> path,int depth) {
        if(depth>48)throw new IllegalArgumentException("Inheritance depth limit");
        if(!key.contains("@"))return canonical(authored,ns);
        String id=qualify(ns,key.split("@",2)[1]);
        if(!path.add(id))throw new IllegalArgumentException("Inheritance cycle: "+id);
        var d=definitions.get(id);if(d==null)throw new IllegalArgumentException("Missing base: "+id);
        var base=resolve(d.key,d.namespace,d.value,path,depth+1);path.remove(id);
        return merge(base,canonical(authored,ns));
    }
    private static JsonObject canonical(JsonObject source,String ns) {
        var out=source.deepCopy();
        if(out.has("anims")&&out.get("anims").isJsonArray()) {
            var refs=new JsonArray();for(var ref:out.getAsJsonArray("anims"))refs.add("@"+qualify(ns,ref.getAsString().replaceFirst("^@","")));out.add("anims",refs);
        }
        if(out.has("controls")&&out.get("controls").isJsonArray()){var children=new JsonArray();for(var control:out.getAsJsonArray("controls")){var child=new JsonObject();for(var e:control.getAsJsonObject().entrySet()){
            var parts=e.getKey().split("@",2);String name=parts.length==1?parts[0]:parts[0]+"@"+qualify(ns,parts[1]);child.add(name,canonical(e.getValue().getAsJsonObject(),ns));}children.add(child);}out.add("controls",children);}
        return out;
    }
    private static void validateTree(JsonElement root) {
        record Work(JsonElement value,int depth){}var pending=new ArrayDeque<Work>();pending.add(new Work(root,0));int visited=0;
        while(!pending.isEmpty()){var work=pending.removeLast();if(work.depth>64||++visited>65536)throw new IllegalArgumentException("UI document structure limit exceeded");
            if(work.value.isJsonArray())for(var v:work.value.getAsJsonArray())pending.add(new Work(v,work.depth+1));
            else if(work.value.isJsonObject())for(var v:work.value.getAsJsonObject().asMap().values())pending.add(new Work(v,work.depth+1));}
    }
    /** Named child overrides retain siblings and tree semantics; unsupported modifications are rejected. */
    private static JsonObject merge(JsonObject base,JsonObject override) {
        var out=base.deepCopy();
        for(var e:override.entrySet()) {
            if(e.getKey().equals("controls")&&out.has("controls")&&out.get("controls").isJsonArray()&&e.getValue().isJsonArray()) {
                var controls=new LinkedHashMap<String,JsonElement>();
                for(var list:List.of(out.getAsJsonArray("controls"),e.getValue().getAsJsonArray()))for(var c:list)for(var child:c.getAsJsonObject().entrySet()) {
                    String name=child.getKey().split("@",2)[0];
                    JsonObject next=new JsonObject();
                    var previous=controls.get(name);
                    String childKey=previous!=null&&!child.getKey().contains("@")?previous.getAsJsonObject().keySet().iterator().next():child.getKey();
                    next.add(childKey,previous==null?child.getValue().deepCopy():merge(previous.getAsJsonObject().entrySet().iterator().next().getValue().getAsJsonObject(),child.getValue().getAsJsonObject()));
                    controls.put(name,next);
                }
                var array=new JsonArray();controls.values().forEach(array::add);out.add("controls",array);
            }else out.add(e.getKey(),e.getValue().deepCopy());
        }
        return out;
    }
    private static JsonElement substitute(JsonElement value,Map<String,JsonElement> scope,Set<String> seen,int depth) {
        if(value==null)throw new IllegalArgumentException("Unavailable UI variable/condition");
        if(depth>48)throw new IllegalArgumentException("Variable/reference depth limit");
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()&&BedrockUiExpression.isExpression(value.getAsString())&&!value.getAsString().contains("#"))
            return BedrockUiExpression.evaluate(value.getAsString(),name->{if(!seen.add(name))throw new IllegalArgumentException("Variable cycle: "+name);var result=substitute(scope.get(name),scope,seen,depth+1);seen.remove(name);return result;});
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()&&value.getAsString().startsWith("$")) {
            String name=value.getAsString();if(!seen.add(name))throw new IllegalArgumentException("Variable cycle: "+name);
            var target=scope.get(name);if(target==null)throw new IllegalArgumentException("Missing variable: "+name);
            var out=substitute(target,scope,seen,depth+1);seen.remove(name);return out;
        }
        if(value.isJsonArray()){var out=new JsonArray();for(var v:value.getAsJsonArray())out.add(substitute(v,scope,seen,depth+1));return out;}
        if(value.isJsonObject()){var out=new JsonObject();value.getAsJsonObject().entrySet().forEach(e->out.add(e.getKey(),substitute(e.getValue(),scope,seen,depth+1)));return out;}
        return value.deepCopy();
    }
    public static String qualify(String ns,String name){return name.contains(".")?name:ns+"."+name;}
    private void error(String file,RuntimeException e){if(errors.size()<256)errors.add(file+": "+e.getMessage());}
}
