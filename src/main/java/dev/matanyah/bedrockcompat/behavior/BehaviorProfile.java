package dev.matanyah.bedrockcompat.behavior;

import net.minecraft.server.packs.resources.ResourceManager;
import java.util.*;
import java.io.*;
import java.util.zip.*;

/** A transaction prepared by resource reload, shared by dedicated and integrated servers. */
public record BehaviorProfile(List<BehaviorPackResources.Content> packs,BehaviorDefinitions definitions,byte[] bundle,String hash,List<BehaviorSubsystem.Prepared> subsystems) {
    public static final BehaviorProfile EMPTY=new BehaviorProfile(List.of(),new BehaviorDefinitions(List.of()),new byte[0],"",List.of());
    public BehaviorProfile {packs=List.copyOf(packs);bundle=bundle.clone();subsystems=List.copyOf(subsystems);}
    @Override public byte[] bundle(){return bundle.clone();}
    public boolean active(){return !packs.isEmpty();}
    public static BehaviorProfile compile(ResourceManager manager) {
        List<BehaviorPackResources> selected=manager.listPacks().filter(BehaviorPackResources.class::isInstance).map(BehaviorPackResources.class::cast).toList();
        Set<String> enabled=new HashSet<>();for(var p:selected)enabled.add(p.pack().uuid());
        Map<String,BehaviorPackResources.Content> all=new LinkedHashMap<>();List<BehaviorPackResources.Content> behaviors=new ArrayList<>();
        for(var p:selected) {
            if(p.content().isEmpty())throw new IllegalArgumentException("Behavior pack has no reload content: "+p.pack().uuid());
            for(var content:p.content()) {
                if(PackCatalog.behavior(content.pack())&&!enabled.contains(content.pack().uuid()))throw new IllegalArgumentException("Enable required behavior datapack: "+PackCatalog.datapackId(content.pack()));
                var old=all.putIfAbsent(content.pack().uuid(),content);
                if(old!=null&&!old.hash().equals(content.hash()))throw new IllegalArgumentException("Inconsistent dependency content: "+content.pack().uuid());
            }
            behaviors.add(p.content().stream().filter(c->c.pack().uuid().equals(p.pack().uuid())).findFirst().orElseThrow());
        }
        if(selected.isEmpty())return EMPTY;
        var definitions=new BehaviorDefinitions(behaviors); // Later packs override earlier definitions, like Java datapacks.
        try {
            var output=new ByteArrayOutputStream();long total=0;
            try(var zip=new ZipOutputStream(output)) {
                for(var c:all.values())for(var f:c.files().entrySet()) {
                    total+=f.getValue().length;if(total>PackCatalog.MAX_BYTES)throw new IOException("Active add-on stack exceeds transfer budget");
                    var e=new ZipEntry(c.pack().uuid()+"/"+f.getKey());e.setTime(0);zip.putNextEntry(e);zip.write(f.getValue());zip.closeEntry();
                }
            }
            List<BehaviorPackResources.Content> ordered=new ArrayList<>(behaviors);all.values().stream().filter(c->!PackCatalog.behavior(c.pack())).forEach(ordered::add);
            byte[] bytes=output.toByteArray();return new BehaviorProfile(ordered,definitions,bytes,PackCatalog.hash(Map.of("bundle",bytes)),BehaviorSubsystem.Registry.prepare(definitions,ordered));
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
}
