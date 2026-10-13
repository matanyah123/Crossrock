package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.Map;

/** Edition-level identifiers only. An absent imported definition never suppresses Java effects. */
public final class BedrockParticleIds {
    private static final Map<String,String> IDS=Map.ofEntries(
        Map.entry("crit","basic_crit_particle"),Map.entry("enchanted_hit","magic_critical_hit_emitter"),
        Map.entry("flame","basic_flame_particle"),Map.entry("soul_fire_flame","blue_flame_particle"),
        Map.entry("smoke","basic_smoke_particle"),Map.entry("large_smoke","large_smoke"),
        Map.entry("bubble","basic_bubble_particle"),Map.entry("splash","water_splash_particle"),
        Map.entry("rain","rain_splash_particle"),Map.entry("portal","portal_directional"),
        Map.entry("enchant","enchanting_table_particle"),Map.entry("totem_of_undying","totem_particle"),
        Map.entry("heart","heart_particle"),Map.entry("happy_villager","villager_happy"),
        Map.entry("angry_villager","villager_angry"),Map.entry("note","note_particle"),
        Map.entry("cloud","water_evaporation_actor_emitter"),Map.entry("poof","explode"),
        Map.entry("explosion","large_explosion"),Map.entry("explosion_emitter","huge_explosion_emitter"),
        Map.entry("dragon_breath","dragon_breath_trail"),Map.entry("end_rod","endrod"),
        Map.entry("campfire_cosy_smoke","campfire_smoke_particle"),Map.entry("campfire_signal_smoke","campfire_tall_smoke_particle"),
        Map.entry("firefly","firefly_particle"),Map.entry("nautilus","conduit_particle"),
        Map.entry("dust","redstone_dust_particle"));
    private BedrockParticleIds() {}
    public static String bedrock(String javaId) {
        if(!javaId.startsWith("minecraft:"))return javaId;
        String path=javaId.substring(10);return "minecraft:"+IDS.getOrDefault(path,path);
    }
}
