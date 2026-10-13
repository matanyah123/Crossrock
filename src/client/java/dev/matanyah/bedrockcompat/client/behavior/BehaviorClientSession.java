package dev.matanyah.bedrockcompat.client.behavior;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.behavior.*;
import dev.matanyah.bedrockcompat.bedrock.discovery.BedrockPackRepository;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackRuntime;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.util.*;
import java.io.*;

/** Connection-scoped downloaded profile. Local RP selections never enable server behavior. */
public final class BehaviorClientSession {
    public static final String PREFIX="crossrock/world_rp/";
    public record Resource(BedrockPack pack,GeneratedResourceSnapshot snapshot){}
    private static Map<String,Resource> resources=Map.of();
    private static BehaviorDefinitions definitions=new BehaviorDefinitions(List.of());
    private static UUID session;private static long revision;private static int expected;private static String hash="";
    private static ByteArrayOutputStream receiving;
    private static List<String> order=List.of();private static boolean loading;private static boolean cleanupReload;
    public static Map<String,Resource> resources(){return resources;}
    public static BehaviorDefinitions definitions(){return definitions;}
    public static boolean ownsGeneration(String generation){return ready()&&session.toString().equals(generation);}
    public static boolean ready(){return session!=null&&!loading;}
    public static void register(){
        ClientPlayNetworking.registerGlobalReceiver(BehaviorPayload.TYPE,(p,c)->receive(p));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->client.execute(()->{if(client.getConnection()==null||client.getConnection()==handler)clear();}));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{if(session!=null&&client.getConnection()==null)clear();if(cleanupReload&&client.level==null&&client.getSingleplayerServer()==null&&client.gui.overlay()==null){cleanupReload=false;client.reloadResourcePacks();}});
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(BehaviorIdentities.ENTITY,BehaviorEntityRenderer::new);
    }
    private static void receive(BehaviorPayload p){
        try {
            if(p.version()!=1)throw new IllegalArgumentException("Unsupported server behavior protocol");
            if(p.kind().equals("offer")) {
                if(session!=null&&p.revision()<revision)return;
                session=p.session();revision=p.revision();loading=true;receiving=null;
                var m=JsonParser.parseString(p.metadata()).getAsJsonObject();expected=m.get("size").getAsInt();hash=m.get("hash").getAsString();
                if(expected<0||expected>PackCatalog.MAX_BYTES)throw new IllegalArgumentException("Oversized world add-on profile");
                order=new ArrayList<>();for(var id:m.getAsJsonArray("packs"))order.add(id.getAsString());
                if(expected==0){if(resources.isEmpty()&&!cleanupReload){definitions=new BehaviorDefinitions(List.of());loading=false;send("ack",hash);}else apply(List.of(),p.session());return;}
                if(!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid world profile hash");
                Path cache=cache();
                if(Files.isRegularFile(cache)&&valid(Files.readAllBytes(cache))){BehaviorBundles.validate(Files.readAllBytes(cache));load(cache,p.session());return;}
                receiving=new ByteArrayOutputStream(Math.min(expected,1024*1024));send("request",hash);
            }else if(p.kind().equals("chunk")&&p.session().equals(session)&&p.revision()==revision&&receiving!=null) {
                int offset=Integer.parseInt(p.metadata());byte[] bytes=p.bytes();
                if(offset!=receiving.size()||offset+bytes.length>expected)throw new IllegalArgumentException("Out-of-order behavior transfer");
                receiving.write(bytes);
                if(receiving.size()==expected){byte[] data=receiving.toByteArray();receiving=null;if(!valid(data))throw new IllegalArgumentException("Behavior profile hash mismatch");
                    BehaviorBundles.validate(data);Path path=cache();Files.createDirectories(path.getParent());Path temp=Files.createTempFile(path.getParent(),"incoming-",".tmp");
                    try{Files.write(temp,data);Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}load(path,p.session());}
            }
        }catch(Exception error){loading=false;receiving=null;send("reject",error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage()).substring(0,Math.min(200,String.valueOf(error.getMessage()).length())));}
    }
    private static Path cache(){return Minecraft.getInstance().gameDirectory.toPath().resolve(".crossrock/server-packs/"+hash+"/profile.mcaddon");}
    private static boolean valid(byte[] data){return data.length==expected&&PackCatalog.hash(Map.of("bundle",data)).equals(hash);}
    private static void load(Path path,UUID generation)throws IOException {
        Map<String,BedrockPack> found=new HashMap<>();
        for(var result:BedrockPackRepository.atDirectory(path.getParent()).scan()) {
            if(result.error().isPresent())throw new IOException(result.error().get());result.pack().ifPresent(pack->{if(found.put(pack.uuid(),pack)!=null)throw new IllegalArgumentException("Duplicate downloaded UUID");});
        }
        List<BedrockPack> packs=new ArrayList<>();for(var id:order){var p=found.remove(id);if(p==null)throw new IOException("Missing downloaded pack "+id);packs.add(p);}
        if(!found.isEmpty())throw new IOException("Unexpected downloaded packs");new PackCatalog(packs).closure(packs);apply(packs,generation);
    }
    private static void apply(List<BedrockPack> packs,UUID generation)throws IOException {
        List<BehaviorPackResources.Content> behaviors=new ArrayList<>();
        for(var p:packs)if(PackCatalog.behavior(p)){var files=PackCatalog.read(p);behaviors.add(new BehaviorPackResources.Content(p,files,PackCatalog.hash(files)));}
        var nextDefinitions=new BehaviorDefinitions(behaviors);
        definitions=nextDefinitions;
        List<dev.matanyah.bedrockcompat.bedrock.resource.BedrockTranslationPipeline.ConfiguredPack> accumulated=new ArrayList<>();
        for(var b:behaviors)accumulated.add(new dev.matanyah.bedrockcompat.bedrock.resource.BedrockTranslationPipeline.ConfiguredPack(b.pack(),BedrockPackConfiguration.defaults(b.pack())));
        Map<String,Resource> next=new LinkedHashMap<>();Map<net.minecraft.resources.Identifier,byte[]> previous=Map.of();
        // Generate ordered prefix differences: each virtual RP owns the resources changed by its layer,
        // while cross-pack atlases/definitions resolve against the full lower-priority stack.
        for(var p:packs)if(p.type()==BedrockPackType.RESOURCE||p.type()==BedrockPackType.MIXED) {
            accumulated.addFirst(new dev.matanyah.bedrockcompat.bedrock.resource.BedrockTranslationPipeline.ConfiguredPack(p,BedrockPackConfiguration.defaults(p)));
            var result=VirtualCompatibilityPackRuntime.generate(accumulated);
            Map<net.minecraft.resources.Identifier,byte[]> delta=new HashMap<>();
            for(var e:result.resources().resources().entrySet())if(!Arrays.equals(previous.get(e.getKey()),e.getValue()))delta.put(e.getKey(),e.getValue());
            next.put(PREFIX+p.uuid(),new Resource(p,new GeneratedResourceSnapshot(delta)));previous=result.resources().resources();
            if(!result.errors().isEmpty())dev.matanyah.bedrockcompat.BedrockCompat.LOGGER.warn("World RP translation diagnostics: {}",result.errors());
        }
        if(!generation.equals(session))return;
        definitions=nextDefinitions;resources=Collections.unmodifiableMap(next);cleanupReload=false;
        var mc=Minecraft.getInstance();var repository=mc.getResourcePackRepository();repository.reload();
        List<String> selected=new ArrayList<>(repository.getSelectedIds().stream().filter(id->!id.startsWith(PREFIX)).toList());selected.addAll(next.keySet());repository.setSelected(selected);
        mc.reloadResourcePacks().whenComplete((ignored,error)->mc.execute(()->{
            if(!generation.equals(session))return;
            loading=false;if(error==null)send("ack",hash);else send("reject","Client resource reload failed");
        }));
    }
    public static void clear(){
        boolean had=!resources.isEmpty();session=null;revision=0;receiving=null;resources=Map.of();definitions=new BehaviorDefinitions(List.of());loading=false;order=List.of();
        if(had){var mc=Minecraft.getInstance();var repo=mc.getResourcePackRepository();repo.setSelected(repo.getSelectedIds().stream().filter(id->!id.startsWith(PREFIX)).toList());repo.reload();cleanupReload=true;}
    }
    private static void send(String kind,String metadata){if(session!=null&&ClientPlayNetworking.canSend(BehaviorPayload.TYPE))ClientPlayNetworking.send(new BehaviorPayload(1,session,revision,kind,metadata,new byte[0]));}
}
