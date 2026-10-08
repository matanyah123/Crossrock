package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.Map;

/** Edition-level variant IDs, independent of any resource pack.
 * Corresponds to Mojang bedrock-samples behavior_pack/entities/villager_v2.json. */
public final class BedrockVillagerVariants {
    private static final Map<String,Integer> PROFESSIONS=Map.ofEntries(
        Map.entry("none",0),Map.entry("farmer",1),Map.entry("fisherman",2),Map.entry("shepherd",3),
        Map.entry("fletcher",4),Map.entry("librarian",5),Map.entry("cartographer",6),Map.entry("cleric",7),
        Map.entry("armorer",8),Map.entry("weaponsmith",9),Map.entry("toolsmith",10),Map.entry("butcher",11),
        Map.entry("leatherworker",12),Map.entry("mason",13),Map.entry("nitwit",14));
    private static final Map<String,Integer> BIOMES=Map.of("plains",0,"desert",1,"jungle",2,"savanna",3,"snow",4,"swamp",5,"taiga",6);
    private BedrockVillagerVariants() {}
    public static int profession(String name){return require(PROFESSIONS,name);}
    public static int biome(String name){return require(BIOMES,name);}
    private static int require(Map<String,Integer> ids,String name) {
        Integer id=ids.get(name.replaceFirst("^minecraft:",""));
        if(id==null)throw new IllegalArgumentException("Unsupported Bedrock villager variant: "+name);
        return id;
    }
}
