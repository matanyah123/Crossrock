package dev.matanyah.bedrockcompat.behavior;

import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.resources.Identifier;
import java.io.*;
import java.util.*;

/** A genuine individual datapack and immutable reload attachment, not generated vanilla gameplay. */
public final class BehaviorPackResources implements PackResources {
    public record Content(BedrockPack pack,Map<String,byte[]> files,String hash) {}
    private final PackLocationInfo location;
    private final BedrockPack pack;
    private final List<Content> closure;
    public BehaviorPackResources(PackLocationInfo location,BedrockPack pack,List<Content> closure){this.location=location;this.pack=pack;this.closure=List.copyOf(closure);}
    public List<Content> content(){return closure;}
    public BedrockPack pack(){return pack;}
    @Override public PackLocationInfo location(){return location;}
    @Override public IoSupplier<InputStream> getRootResource(String... names){
        if(names.length==1&&names[0].equals("pack.png"))return pack.icon().map(b->(IoSupplier<InputStream>)()->new ByteArrayInputStream(b)).orElse(null);return null;
    }
    @Override public IoSupplier<InputStream> getResource(PackType type,Identifier id){
        if(type!=PackType.SERVER_DATA||!id.equals(marker()))return null;
        return ()->new ByteArrayInputStream(("{\"uuid\":\""+pack.uuid()+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private Identifier marker(){return Identifier.fromNamespaceAndPath("bedrock_compat","behavior_packs/"+pack.uuid()+".json");}
    @Override public void listResources(PackType type,String ns,String path,ResourceOutput out){if(type==PackType.SERVER_DATA&&ns.equals("bedrock_compat")&&marker().getPath().startsWith(path))out.accept(marker(),getResource(type,marker()));}
    @Override public Set<String> getNamespaces(PackType type){return type==PackType.SERVER_DATA?Set.of("bedrock_compat"):Set.of();}
    @Override public <T>T getMetadataSection(MetadataSectionType<T> type){return null;}
    @Override public void close(){}
}
