package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** Edition identifier differences, not pack-specific entity overrides. */
public final class BedrockEntityIds {
    private BedrockEntityIds() {}
    private static final Map<String,String> IDS=Map.ofEntries(
        Map.entry("minecraft:villager","minecraft:villager_v2"),
        Map.entry("minecraft:zombie_villager","minecraft:zombie_villager_v2"),
        Map.entry("minecraft:zombified_piglin","minecraft:zombie_pigman"),
        Map.entry("minecraft:evoker","minecraft:evocation_illager"),
        Map.entry("minecraft:evoker_fangs","minecraft:evocation_fang"),
        Map.entry("minecraft:tropical_fish","minecraft:tropicalfish"),
        Map.entry("minecraft:end_crystal","minecraft:ender_crystal"),
        Map.entry("minecraft:eye_of_ender","minecraft:eye_of_ender_signal"),
        Map.entry("minecraft:firework_rocket","minecraft:fireworks_rocket"),
        Map.entry("minecraft:experience_bottle","minecraft:xp_bottle"),
        Map.entry("minecraft:experience_orb","minecraft:xp_orb"),
        Map.entry("minecraft:trident","minecraft:thrown_trident"),
        Map.entry("minecraft:wind_charge","minecraft:wind_charge_projectile"));
    // Bedrock uses one entity plus a variant; Java registers each wood separately.
    private static final List<String> BOAT_WOODS=List.of("oak","spruce","birch","jungle","acacia",
        "dark_oak","mangrove","bamboo","cherry","pale_oak");
    public record Boat(String wood,int variant,boolean chest) {
        public String identifier(){return chest?"minecraft:chest_boat":"minecraft:boat";}
        public String javaTexture(){return "minecraft:textures/entity/"+(chest?"chest_boat/":"boat/")+wood+".png";}
    }
    private static final Map<String,Boat> BOATS=boats();
    private static Map<String,Boat> boats() {
        Map<String,Boat> result=new HashMap<>();
        for(int i=0;i<BOAT_WOODS.size();i++) {
            String wood=BOAT_WOODS.get(i),kind=wood.equals("bamboo")?"raft":"boat";
            result.put("minecraft:"+wood+"_"+kind,new Boat(wood,i,false));
            result.put("minecraft:"+wood+"_chest_"+kind,new Boat(wood,i,true));
        }
        return Map.copyOf(result);
    }
    public static Optional<Boat> boat(String javaId) {
        return Optional.ofNullable(BOATS.get(javaId));
    }
    public static String bedrock(String javaId){return boat(javaId).map(Boat::identifier).orElseGet(()->IDS.getOrDefault(javaId,javaId));}
    public static float bodyYaw(String javaId,float javaYaw){return BOATS.containsKey(javaId)?javaYaw+90:javaYaw;}
}
