package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** Edition-level biome vocabulary. Never guesses categories from a custom biome's name. */
public final class BedrockBiomes {
    private static final Map<String,String> ALIASES=new HashMap<>();
    private static final Map<String,Set<String>> TAGS=new HashMap<>();
    static {
        aliases("snowy_plains","ice_plains ice_mountains");
        aliases("ice_spikes","ice_plains_spikes");
        aliases("snowy_beach","cold_beach");
        aliases("snowy_taiga","cold_taiga cold_taiga_hills cold_taiga_mutated");
        aliases("frozen_ocean","legacy_frozen_ocean");
        aliases("windswept_hills","extreme_hills extreme_hills_edge");
        aliases("windswept_gravelly_hills","extreme_hills_mutated");
        aliases("windswept_forest","extreme_hills_plus_trees extreme_hills_plus_trees_mutated");
        aliases("dark_forest","roofed_forest roofed_forest_mutated");
        aliases("badlands","mesa mesa_plateau mesa_plateau_mutated");
        aliases("eroded_badlands","mesa_bryce");
        aliases("wooded_badlands","mesa_plateau_stone mesa_plateau_stone_mutated");
        aliases("old_growth_pine_taiga","mega_taiga mega_taiga_hills");
        aliases("old_growth_spruce_taiga","redwood_taiga_mutated redwood_taiga_hills_mutated");
        aliases("taiga","taiga_hills taiga_mutated");
        aliases("birch_forest","birch_forest_hills");
        aliases("old_growth_birch_forest","birch_forest_mutated birch_forest_hills_mutated");
        aliases("forest","forest_hills");aliases("desert","desert_hills desert_mutated");
        aliases("swamp","swampland swampland_mutated");aliases("jungle","jungle_hills jungle_mutated");
        aliases("sparse_jungle","jungle_edge jungle_edge_mutated");aliases("bamboo_jungle","bamboo_jungle_hills");
        aliases("windswept_savanna","savanna_mutated savanna_plateau_mutated");
        aliases("nether_wastes","hell");aliases("soul_sand_valley","soulsand_valley");
        aliases("stony_shore","stone_beach");aliases("mushroom_fields","mushroom_island mushroom_island_shore");
        group("plains sunflower_plains","animal monster plains overworld bee_habitat");
        group("forest","animal monster forest overworld");
        group("flower_forest","flower_forest forest monster mutated overworld bee_habitat");
        group("birch_forest old_growth_birch_forest","animal monster birch forest overworld bee_habitat");
        group("dark_forest pale_garden","animal forest monster overworld roofed dark_oak");
        group("taiga snowy_taiga old_growth_pine_taiga old_growth_spruce_taiga","animal forest monster overworld taiga");
        group("old_growth_pine_taiga old_growth_spruce_taiga","mega");
        group("snowy_taiga snowy_beach cold_ocean deep_cold_ocean grove","cold");
        group("jungle sparse_jungle bamboo_jungle","animal jungle monster overworld");
        group("sparse_jungle","edge");group("bamboo_jungle","bamboo");
        group("desert","monster desert overworld");
        group("badlands eroded_badlands wooded_badlands","mesa monster overworld");
        group("wooded_badlands","plateau stone");group("eroded_badlands","mutated animal");
        group("savanna savanna_plateau windswept_savanna","animal monster savanna overworld");
        group("savanna_plateau","plateau");group("windswept_savanna","mutated");
        group("swamp","animal monster overworld swamp");group("mangrove_swamp","overworld mangrove_swamp");
        group("snowy_plains ice_spikes","frozen ice ice_plains overworld");group("ice_spikes","mutated monster");
        group("windswept_hills windswept_gravelly_hills windswept_forest","animal extreme_hills monster overworld mountain");
        group("windswept_forest","forest");
        group("frozen_peaks jagged_peaks snowy_slopes","mountain monster overworld frozen");
        group("stony_peaks meadow cherry_grove grove","mountain monster overworld");
        group("meadow cherry_grove","bee_habitat");
        group("ocean deep_ocean cold_ocean deep_cold_ocean frozen_ocean deep_frozen_ocean lukewarm_ocean deep_lukewarm_ocean warm_ocean","ocean monster overworld");
        group("deep_ocean deep_cold_ocean deep_frozen_ocean deep_lukewarm_ocean","deep");
        group("frozen_ocean deep_frozen_ocean frozen_river","frozen");
        group("lukewarm_ocean deep_lukewarm_ocean","lukewarm");group("warm_ocean beach","warm");
        group("river frozen_river","river overworld");
        group("beach snowy_beach stony_shore","beach monster overworld");group("stony_shore","stone");
        group("mushroom_fields","mooshroom_island overworld");
        group("lush_caves dripstone_caves deep_dark","caves overworld");group("lush_caves dripstone_caves","monster");
        group("nether_wastes crimson_forest warped_forest soul_sand_valley basalt_deltas","nether");
        group("crimson_forest warped_forest","netherwart_forest");group("soul_sand_valley","soulsand_valley");
        group("the_end end_highlands end_midlands small_end_islands end_barrens","the_end");
        group("cherry_grove grove frozen_peaks jagged_peaks snowy_slopes stony_peaks meadow pale_garden deep_dark lush_caves dripstone_caves crimson_forest warped_forest basalt_deltas nether_wastes","");
        for(String id:new HashSet<>(TAGS.keySet()))TAGS.get(id).add(id.substring(10));
        TAGS.replaceAll((id,tags)->Set.copyOf(tags));
    }
    private BedrockBiomes() {}
    private static void aliases(String javaName,String bedrockNames){for(String name:bedrockNames.split(" "))ALIASES.put("minecraft:"+name,"minecraft:"+javaName);}
    private static void group(String biomes,String tags){for(String id:biomes.split(" ")) {
        var values=TAGS.computeIfAbsent("minecraft:"+id,k->new HashSet<>());
        if(!tags.isEmpty())values.addAll(List.of(tags.split(" ")));
    }}
    public static String identifier(String id){String qualified=id.contains(":")?id:"minecraft:"+id;return ALIASES.getOrDefault(qualified,qualified);}
    public static Set<String> tags(String biome,Set<String> javaTags) {
        var result=new HashSet<>(javaTags);result.addAll(TAGS.getOrDefault(identifier(biome),Set.of()));
        for(String tag:javaTags)if(tag.startsWith("minecraft:is_")) {
            String semantic=tag.substring(13);
            result.add(switch(semantic){case "badlands"->"mesa";case "hill"->"hills";case "end"->"the_end";default->semantic;});
        }
        return Set.copyOf(result);
    }
}
