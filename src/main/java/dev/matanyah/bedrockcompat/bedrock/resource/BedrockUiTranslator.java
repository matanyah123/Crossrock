package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.matanyah.bedrockcompat.bedrock.ui.BedrockUiTemplates;

/** Publishes registered UI files for reload-time compilation. Does not touch Java widgets. */
public final class BedrockUiTranslator implements BedrockResourceTranslator {
    private final BedrockPackContentReader reader;
    public BedrockUiTranslator(BedrockPackContentReader reader){this.reader=reader;}
    @Override public void translate(BedrockPack pack,BedrockPackConfiguration config,GeneratedResourceSink sink)throws IOException {
        var files=new LinkedHashMap<String,byte[]>();
        long[] total={0};
        reader.visitUi(pack,config.selectedSubpack(),(path,bytes)-> {
            if(bytes.length>1024*1024||(total[0]+=bytes.length)>16*1024*1024||files.size()>=1024)throw new IOException("UI resource limits exceeded: "+path);
            files.put(path,bytes);
        });
        var extra=new LinkedHashSet<String>();
        try{if(files.containsKey("ui/_ui_defs.json"))for(var entry:parse(files.get("ui/_ui_defs.json")).getAsJsonArray("ui_defs")){String path=entry.getAsString();if(safe(path)&&!files.containsKey(path)&&extra.size()<1024)extra.add(path);}}catch(RuntimeException ignored){/* publish records malformed registration */}
        if(!extra.isEmpty())reader.visitUiRegistered(pack,config.selectedSubpack(),extra,(path,bytes)->{
            if(bytes.length>1024*1024||(total[0]+=bytes.length)>16*1024*1024||files.size()>=1024)throw new IOException("UI resource limits exceeded: "+path);files.put(path,bytes);
        });
        publish(files,sink);
    }
    public static void publish(Map<String,byte[]> files,GeneratedResourceSink sink)throws IOException {
        var selected=new LinkedHashSet<String>();var errors=new ArrayList<String>();
        if(files.containsKey("ui/_ui_defs.json"))try {
            var defs=parse(files.get("ui/_ui_defs.json")).getAsJsonArray("ui_defs");
            if(defs.size()>1024)throw new IllegalArgumentException("UI registration limit exceeded");
            for(var path:defs) {
                try{String p=path.getAsString();if(!safe(p))throw new IllegalArgumentException("Unsafe UI path: "+p);selected.add(p);}
                catch(RuntimeException e){errors.add("ui/_ui_defs.json: "+e.getMessage());}
            }
        }catch(RuntimeException e){errors.add("ui/_ui_defs.json: "+e.getMessage());}
        if(files.containsKey("ui/_global_variables.json"))selected.add("ui/_global_variables.json");
        // Bedrock's built-in screen files can be patched without _ui_defs registration.
        BedrockUiTemplates.FILE_NAMESPACES.keySet().stream().filter(files::containsKey).sorted().forEach(selected::add);
        for(String p:selected)try {
            if(!files.containsKey(p))throw new IllegalArgumentException("Registered file missing");
            var root=parse(files.get(p));
            sink.put(Identifier.fromNamespaceAndPath("bedrock_native","json_ui/"+HexFormat.of().formatHex(p.getBytes(StandardCharsets.UTF_8))+".json"),
                ("{\"source\":"+new Gson().toJson(p)+",\"document\":"+root+"}").getBytes(StandardCharsets.UTF_8));
        }catch(RuntimeException e){errors.add(p+": "+e.getMessage());}
        if(!errors.isEmpty())throw new IOException(String.join("; ",errors));
    }
    public static boolean safe(String p){
        // Registration is pack-relative, not ui-directory-relative. Never resolve host paths.
        if(p.isEmpty()||p.length()>1024||!p.matches("[a-zA-Z0-9_./ -]+"))return false;
        return Arrays.stream(p.split("/",-1)).noneMatch(s->s.isEmpty()||s.equals(".")||s.equals(".."));
    }
    private static JsonObject parse(byte[] bytes){
        if(bytes.length>1024*1024)throw new IllegalArgumentException("UI document exceeds 1 MiB");
        return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
