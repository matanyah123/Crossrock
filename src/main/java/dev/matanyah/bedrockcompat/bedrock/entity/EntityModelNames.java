package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** Vanilla/CEM naming conventions, not an EMF dependency or pack-specific rule engine. */
public final class EntityModelNames {
    private EntityModelNames() {}
    private static final Set<String> BABIES=Set.of("armadillo","axolotl","bee","camel","camel_husk","cat",
        "chicken","cow","dolphin","donkey","drowned","fox","glow_squid","goat","hoglin","horse",
        "husk","llama","mooshroom","mule","nautilus","ocelot","panda","pig","piglin","polar_bear",
        "rabbit","sheep","skeleton_horse","sniffer","squid","strider","trader_llama","turtle",
        "villager","wolf","zoglin","zombie","zombie_horse","zombie_villager","zombified_piglin");
    private static final Set<String> WOODS=Set.of("oak","spruce","birch","jungle","acacia","cherry",
        "dark_oak","pale_oak","mangrove");
    public static Set<String> candidates(String entity, boolean baby, Map<String,Object> facts) {
        int colon=entity.indexOf(':');
        String namespace=colon<0?"minecraft":entity.substring(0,colon);
        String name=entity.substring(colon+1);
        // Modded entity names must never inherit vanilla conventions just because a suffix matches.
        if(!namespace.equals("minecraft"))return Set.of(entity);
        Set<String> names=new LinkedHashSet<>();names.add(name);
        switch(name) {
            case "ender_dragon" -> names.add("dragon");
            case "leash_knot" -> names.add("lead_knot");
            case "armor_stand" -> {if(baby)names.add("armor_stand_small");}
            case "pufferfish" -> names.add(switch(number(facts,"puff_state")) {
                case 0 -> "puffer_fish_small"; case 1 -> "puffer_fish_medium"; default -> "puffer_fish_big";
            });
            case "tropical_fish" -> {
                String shape=flag(facts,"large_fish")?"b":"a";
                names.add("tropical_fish_"+shape);names.add("tropical_fish_pattern_"+shape);
            }
            case "sheep" -> {if(!flag(facts,"is_sheared"))names.add("sheep_wool");}
            case "creeper" -> {if(flag(facts,"is_powered"))names.add("creeper_charge");}
            case "bogged","drowned","stray","slime" -> names.add(name+"_outer");
            case "breeze" -> names.add("breeze_wind");
            case "wither" -> {if(flag(facts,"is_powered"))names.add("wither_armor");}
            case "happy_ghast" -> {if(baby)names.add("happy_ghast_baby");if(flag(facts,"has_rider"))names.add("happy_ghast_ropes");}
        }
        if(flag(facts,"is_saddled"))names.add(name+"_saddle");
        if(flag(facts,"has_body_equipment")) {
            if(Set.of("horse","skeleton_horse","zombie_horse","wolf").contains(name))names.add(name+"_armor");
            if(Set.of("llama","trader_llama").contains(name))names.add(name+"_decor");
            if(name.equals("happy_ghast"))names.add("happy_ghast_harness");
        }
        if(flag(facts,"is_tamed")&&Set.of("cat","wolf").contains(name))names.add(name+"_collar");
        if(Set.of("chicken","cow","pig").contains(name)) {
            Object properties=facts.get("properties");
            if(properties instanceof Map<?,?> map) {
                Object climate=map.get("minecraft:climate_variant");
                if(climate instanceof String text&&Set.of("cold","warm").contains(text))names.add(text+"_"+name);
            }
        }
        if(baby&&BABIES.contains(name)) {
            // Body and layer fallback names remain eligible; add the age-specific alternatives.
            for(String model:List.copyOf(names)) {
                if(model.equals(name))names.add(name+"_baby");
                else if(model.startsWith(name+"_"))names.add(name+"_baby"+model.substring(name.length()));
                else if(model.equals("cold_"+name)||model.equals("warm_"+name))names.add(model+"_baby");
            }
        }
        for(String wood:WOODS) {
            if(name.equals(wood+"_boat")) {names.add("boat");names.add("boat_patch");names.add(wood+"_boat_patch");}
            if(name.equals(wood+"_chest_boat")) {names.add("chest_boat");names.add("boat_patch");names.add(wood+"_chest_boat_patch");}
        }
        if(name.equals("bamboo_raft"))names.add("raft");
        if(name.equals("bamboo_chest_raft"))names.add("chest_raft");
        if(Set.of("chest_minecart","command_block_minecart","furnace_minecart","hopper_minecart",
                "tnt_minecart","spawner_minecart").contains(name))names.add("minecart");
        Set<String> result=new LinkedHashSet<>();names.forEach(model->result.add(namespace+":"+model));
        return Set.copyOf(result);
    }
    private static boolean flag(Map<String,Object> facts,String name) {
        Object value=facts.get(name);return value instanceof Boolean b?b:value instanceof Number n&&n.doubleValue()!=0;
    }
    private static int number(Map<String,Object> facts,String name) {
        Object value=facts.get(name);return value instanceof Number n?n.intValue():0;
    }
}
