package dev.matanyah.bedrockcompat.behavior;

import dev.matanyah.bedrockcompat.bedrock.discovery.BedrockPackRepository;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import dev.matanyah.bedrockcompat.bedrock.resource.BedrockPackContentReader;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.security.*;

/** Shared discovery/identity policy; world-local addons override installation-local addons. */
public final class PackCatalog {
    public static final String PREFIX="crossrock/bp/";
    public static final int MAX_BYTES=128*1024*1024;
    private final Map<String,BedrockPack> packs;
    public PackCatalog(List<BedrockPack> input) {
        Map<String,BedrockPack> found=new LinkedHashMap<>();
        for(var p:input) {
            UUID.fromString(p.uuid());
            String key=p.uuid().toLowerCase(Locale.ROOT);
            var previous=found.putIfAbsent(key,p);
            if(previous!=null&&!previous.source().equals(p.source()))
                throw new IllegalArgumentException("Ambiguous pack UUID "+key+": "+previous.source()+" and "+p.source());
        }
        packs=Map.copyOf(found);
    }
    public static PackCatalog discover(Path game,Path datapacks) {
        Map<String,BedrockPack> found=new LinkedHashMap<>();
        for(Path root:List.of(game.resolve("addons"),datapacks.resolve("addons"))) {
            Set<String> local=new HashSet<>();
            for(var result:BedrockPackRepository.atDirectory(root).scan()) {
                if(result.error().isPresent())dev.matanyah.bedrockcompat.BedrockCompat.LOGGER.warn("Skipping invalid add-on {}: {}",result.source(),result.error().get());
                result.pack().ifPresent(p->{String key=p.uuid().toLowerCase(Locale.ROOT);
                    if(!local.add(key))throw new IllegalArgumentException("Duplicate pack UUID in "+root+": "+key);
                    found.put(key,p);});
            }
        }
        return new PackCatalog(new ArrayList<>(found.values()));
    }
    public Collection<BedrockPack> packs(){return packs.values();}
    public BedrockPack require(String uuid){var p=packs.get(uuid.toLowerCase(Locale.ROOT));if(p==null)throw new IllegalArgumentException("Missing Bedrock dependency "+uuid);return p;}
    public static boolean behavior(BedrockPack p){return p.type()==BedrockPackType.BEHAVIOR||p.type()==BedrockPackType.MIXED;}
    public static String datapackId(BedrockPack p){return PREFIX+p.uuid().toLowerCase(Locale.ROOT);}
    public List<BedrockPack> closure(Collection<BedrockPack> roots) {
        Map<String,BedrockPack> result=new LinkedHashMap<>();
        Set<String> seen=new HashSet<>();for(var p:roots)visit(p,result,seen);
        return List.copyOf(result.values());
    }
    private void visit(BedrockPack p,Map<String,BedrockPack> result,Set<String> seen) {
        if(!seen.add(p.uuid()))return; // Mutual BP/RP dependencies are legal.
        for(var d:p.dependencies())if(d.moduleName()==null||d.moduleName().isBlank()) {
            var target=require(d.uuid());
            if(d.version().isKnown()&&!d.version().equals(target.version()))throw new IllegalArgumentException("Dependency version mismatch: "+d.uuid()+" requires "+d.version()+", found "+target.version());
            visit(target,result,seen);
        }
        result.put(p.uuid(),p);
    }
    public static Map<String,byte[]> read(BedrockPack p) throws IOException {
        Map<String,byte[]> files=new TreeMap<>();long[] total={0};
        new BedrockPackContentReader().visitAll(p,Optional.empty(),(path,bytes)->{
            safePath(path);total[0]+=bytes.length;if(total[0]>MAX_BYTES)throw new IOException("Pack exceeds content limit: "+p.displayName());
            files.put(path,bytes.clone());
        });
        return Collections.unmodifiableMap(files);
    }
    public static void safePath(String path) {
        if(path.isBlank()||path.startsWith("/")||path.contains("\\")||path.contains(":")||Arrays.asList(path.split("/")).contains(".."))throw new IllegalArgumentException("Unsafe pack path: "+path);
    }
    public static String hash(Map<String,byte[]> files) {
        try {var digest=MessageDigest.getInstance("SHA-256");
            for(var e:new TreeMap<>(files).entrySet()) {
                byte[] name=e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(name.length).array());digest.update(name);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(e.getValue().length).array());digest.update(e.getValue());
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
