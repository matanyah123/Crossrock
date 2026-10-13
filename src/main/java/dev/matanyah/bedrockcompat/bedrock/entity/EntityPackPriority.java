package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** Resource-source ordering, independent of the presence or APIs of rendering mods. */
public final class EntityPackPriority {
    private final int nativeRank;
    private Map<String,Integer> actors=Map.of();
    private final Map<String,Integer> textures, models;
    public EntityPackPriority(String nativePack,Map<String,Integer> ranks,Map<String,String> sources) {
        nativeRank=ranks.getOrDefault(nativePack,-1);
        Map<String,Integer> textureRanks=new HashMap<>(),modelRanks=new HashMap<>();
        sources.forEach((resource,pack)-> {
            int rank=ranks.getOrDefault(pack,-1);
            int colon=resource.indexOf(':');
            String namespace=colon<0?"minecraft":resource.substring(0,colon);
            String path=resource.substring(colon+1);
            if(path.startsWith("textures/entity/")||path.startsWith("optifine/random/entity/")||path.startsWith("optifine/mob/")) {
                textureRanks.merge(canonicalTexture(namespace+":"+path),rank,Math::max);
            }
            if(path.startsWith("optifine/cem/")||path.startsWith("emf/cem/")) {
                if(!(path.endsWith(".jem")||path.endsWith(".properties")))return;
                String stem=path.substring(path.lastIndexOf('/')+1).replaceFirst("(?:[0-9]+)?\\.(?:jem|properties)$","");
                modelRanks.merge(namespace+":"+stem,rank,Math::max);
            }
        });
        textures=Map.copyOf(textureRanks);models=Map.copyOf(modelRanks);
    }
    public EntityPackPriority(String nativePack,Map<String,Integer> ranks,Map<String,String> sources,Map<String,String> actorSources) {
        this(nativePack,ranks,sources);Map<String,Integer> values=new HashMap<>();actorSources.forEach((id,pack)->values.put(id,ranks.getOrDefault(pack,-1)));actors=Map.copyOf(values);
    }
    public boolean owns(String entity,String texture) {return owns(entity,texture,Set.of(entity));}
    public boolean owns(String entity,String texture,Collection<String> modelNames) {
        int rank=actors.getOrDefault(entity,nativeRank);return rank>=0&&textures.getOrDefault(canonicalTexture(texture),-1)<=rank&&modelNames.stream().noneMatch(model->models.getOrDefault(model,-1)>rank);
    }
    public boolean owns(String texture,Collection<String> modelNames) {
        return nativeRank>=0 && textures.getOrDefault(canonicalTexture(texture),-1)<=nativeRank
            && modelNames.stream().noneMatch(model->models.getOrDefault(model,-1)>nativeRank);
    }
    private static String canonicalTexture(String id) {
        String result=id.replace(":optifine/random/entity/",":textures/entity/").replace(":optifine/mob/",":textures/entity/");
        if(result.endsWith(".properties"))result=result.substring(0,result.length()-11)+".png";
        return result.replaceFirst("[0-9]+\\.png$",".png");
    }
}
