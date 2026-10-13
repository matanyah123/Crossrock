package dev.matanyah.bedrockcompat.bedrock.ui;

import com.google.gson.*;
import java.util.*;

/** Bounded, reload-time authored dependency inventory. Resolution is NOT execution validation.
 * Conditional/variable edges are deliberately retained as candidates, not claimed active paths. */
public final class BedrockUiDependencies {
    public record Definition(String id, String file, String pointer, String ownership) {}
    public record Edge(String from, String file, String pointer, String reference, String expected,
                       String status, String ownership) {}
    public record Observation(String definition, String file, String pointer, String kind, String value) {}
    public record Failure(String stage, String file, String pointer, List<String> chain, String message) {}
    public record Report(List<Definition> definitions, List<Edge> edges, List<Observation> observations,
                         List<Failure> failures) {
        public List<Edge> reachable(String root) {
            var pending=new ArrayDeque<String>(); pending.add(root); var visited=new HashSet<String>();
            var result=new ArrayList<Edge>();
            while(!pending.isEmpty()) {
                String id=pending.removeFirst(); if(!visited.add(id))continue;
                for(var edge:edges)if(edge.from.equals(id)){result.add(edge);if(edge.status.equals("resolved"))pending.add(edge.reference);}
            }
            return List.copyOf(result);
        }
    }
    private record Authored(Definition info, String namespace, String key, JsonObject value) {}
    public static Report inspect(List<BedrockUi.Source> sources) {
        var index=new LinkedHashMap<String,Authored>();var failures=new ArrayList<Failure>();
        var all=new ArrayList<>(BedrockUiTemplates.primitives());all.addAll(sources);
        for(var source:all)try {
            if(source.file().endsWith("_global_variables.json"))continue;
            String ns=BedrockUiTemplates.namespace(source);
            for(var entry:source.document().entrySet())if(!entry.getKey().equals("namespace")){
                if(index.size()>=4096)throw new IllegalArgumentException("Dependency definition limit");
                String id=BedrockUi.qualify(ns,entry.getKey().split("@",2)[0]);
                var info=new Definition(id,source.file(),"/"+escape(entry.getKey()),source.file().equals(BedrockUiTemplates.SOURCE)?"engine-compatibility":"pack");
                index.put(id,new Authored(info,ns,entry.getKey(),entry.getValue().getAsJsonObject()));
            }
        }catch(RuntimeException e){failures.add(new Failure("parse",source.file(),"",List.of(),e.getMessage()));}
        var edges=new ArrayList<Edge>();var observations=new ArrayList<Observation>();
        for(var authored:index.values())try {
            scan(authored,authored.key,authored.value,authored.info.pointer,index,edges,observations,0,new int[]{0});
        }catch(RuntimeException e){failures.add(new Failure("safety",authored.info.file,authored.info.pointer,List.of(authored.info.id),e.getMessage()));}
        for(var source:sources)if(source.file().endsWith("_global_variables.json"))try {
            var info=new Definition("<globals>",source.file(),"","pack");
            scan(new Authored(info,"","",source.document()),"",source.document(),"",index,edges,observations,0,new int[]{0});
        }catch(RuntimeException e){failures.add(new Failure("safety",source.file(),"",List.of("<globals>"),e.getMessage()));}
        for(var edge:edges)if(edge.status.equals("missing"))failures.add(new Failure("reference-resolution",edge.file,edge.pointer,List.of(edge.from,edge.reference),"Missing "+edge.expected+"; ownership unknown, not assumed engine-provided"));
        return new Report(index.values().stream().map(Authored::info).toList(),List.copyOf(edges),List.copyOf(observations),List.copyOf(failures));
    }
    private static void scan(Authored a,String key,JsonElement value,String pointer,Map<String,Authored> index,
                             List<Edge> edges,List<Observation> observations,int depth,int[] count) {
        if(depth>64||++count[0]>65536||edges.size()+observations.size()>65536)throw new IllegalArgumentException("Dependency scan resource limit");
        if(key.contains("@"))edge(a,pointer,key.substring(key.indexOf('@')+1),"template",index,edges);
        if(value.isJsonObject())for(var e:value.getAsJsonObject().entrySet())scan(a,e.getKey(),e.getValue(),pointer+"/"+escape(e.getKey()),index,edges,observations,depth+1,count);
        else if(value.isJsonArray()){int i=0;for(var e:value.getAsJsonArray())scan(a,"",e,pointer+"/"+i++,index,edges,observations,depth+1,count);}
        else if(value.isJsonPrimitive()){
            String text=value.getAsString();
            if(key.equals("grid_item_template")||key.equals("control_name"))edge(a,pointer,text.replaceFirst("^@",""),"control",index,edges);
            else if(text.startsWith("@"))edge(a,pointer,text.substring(1),"animation",index,edges);
            else if(key.startsWith("$")&&text.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))edge(a,pointer,text,"variable-template-candidate",index,edges);
            String kind=switch(key){case "binding_name","source_control_name","source_property_name","target_property_name","binding_type","binding_condition"->"binding";case "collection_name"->"collection";case "texture"->"texture";case "type","renderer","operation","array_name","grid_dimensions","requires"->key;default->key.startsWith("$")?"variable":"";};
            if(!kind.isEmpty())observations.add(new Observation(a.info.id,a.info.file,pointer,kind,text));
        }
    }
    private static void edge(Authored a,String pointer,String ref,String expected,Map<String,Authored> index,List<Edge> edges){
        boolean dynamic=ref.contains("$");String id=dynamic?ref:BedrockUi.qualify(a.namespace,ref);
        var target=index.get(id);edges.add(new Edge(a.info.id,a.info.file,pointer,id,expected,dynamic?"dynamic":target==null?(expected.equals("variable-template-candidate")?"unresolved-candidate":"missing"):"resolved",target==null?"unknown":target.info.ownership));
    }
    private static String escape(String value){return value.replace("~","~0").replace("/","~1");}
    private BedrockUiDependencies(){}
}
