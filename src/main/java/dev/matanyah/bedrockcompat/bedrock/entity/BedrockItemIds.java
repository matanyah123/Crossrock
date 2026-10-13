package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** Edition vocabulary only: no pack-owned identifiers or behavior belong here. */
public final class BedrockItemIds {
    private BedrockItemIds() {}
    private static final Map<String,String> JAVA_TO_BEDROCK=Map.ofEntries(
        Map.entry("item_frame","frame"),Map.entry("glow_item_frame","glow_frame"),
        Map.entry("cobweb","web"),Map.entry("magma_block","magma"),
        Map.entry("jack_o_lantern","lit_pumpkin"),Map.entry("rooted_dirt","dirt_with_roots"),
        Map.entry("note_block","noteblock"),Map.entry("powered_rail","golden_rail"),
        Map.entry("dead_bush","deadbush"));
    public static String bedrock(String identifier) {
        if(!identifier.startsWith("minecraft:"))return identifier;
        return "minecraft:"+JAVA_TO_BEDROCK.getOrDefault(identifier.substring(10),identifier.substring(10));
    }
    public static boolean matches(String javaId,String authoredId) {
        return javaId.equals(authoredId)||bedrock(javaId).equals(authoredId);
    }
    public static Set<String> tags(String identifier,Set<String> registryTags) {
        Set<String> result=new HashSet<>(registryTags);
        if(identifier.startsWith("minecraft:")) {
            String name=identifier.substring(10);
            for(String tier:List.of("leather","chainmail","copper","iron","golden","diamond","netherite"))
                for(String piece:List.of("helmet","chestplate","leggings","boots"))
                    if(name.equals(tier+"_"+piece)) {
                        result.add("minecraft:is_armor");
                        result.add("minecraft:"+tier+"_tier");
                        if(tier.equals("golden"))result.add("minecraft:gold_tier");
                    }
            if(name.endsWith("_harness")||name.equals("harness"))result.add("minecraft:harness");
        }
        return Set.copyOf(result);
    }
}
