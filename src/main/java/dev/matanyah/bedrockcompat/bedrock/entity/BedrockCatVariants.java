package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.Map;

/** Edition IDs from Mojang's cat behavior definition, not Java registry order. */
public final class BedrockCatVariants {
    private static final Map<String,Integer> IDS=Map.ofEntries(
        Map.entry("white",0),Map.entry("black",1),Map.entry("red",2),Map.entry("siamese",3),
        Map.entry("british_shorthair",4),Map.entry("calico",5),Map.entry("persian",6),
        Map.entry("ragdoll",7),Map.entry("tabby",8),Map.entry("all_black",9),Map.entry("jellie",10));
    private BedrockCatVariants() {}
    public static int variant(String identifier) {
        String name=identifier.startsWith("minecraft:")?identifier.substring(10):identifier;
        Integer value=IDS.get(name);
        if(value==null)throw new IllegalArgumentException("Unsupported Bedrock cat variant: "+identifier);
        return value;
    }
}
