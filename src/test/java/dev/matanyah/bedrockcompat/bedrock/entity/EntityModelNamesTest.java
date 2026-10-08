package dev.matanyah.bedrockcompat.bedrock.entity;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EntityModelNamesTest {
    private Set<String> names(String entity,boolean baby,Map<String,Object> facts) {
        return EntityModelNames.candidates("minecraft:"+entity,baby,facts);
    }
    @Test void editionAndCemNamesAreNotAssumedToBeEntityIds() {
        assertTrue(names("ender_dragon",false,Map.of()).contains("minecraft:dragon"));
        assertTrue(names("leash_knot",false,Map.of()).contains("minecraft:lead_knot"));
        for(int puff=0;puff<3;puff++) {
            var candidates=names("pufferfish",false,Map.of("puff_state",puff));
            assertTrue(candidates.contains("minecraft:puffer_fish_"+List.of("small","medium","big").get(puff)));
            assertEquals(2,candidates.size());
        }
        assertTrue(names("tropical_fish",false,Map.of("large_fish",true)).contains("minecraft:tropical_fish_pattern_b"));
        assertFalse(names("tropical_fish",false,Map.of()).contains("minecraft:tropical_fish_b"));
    }
    @Test void ageLayersAndClimateFollowActualEntityFacts() {
        var candidates=names("cow",true,Map.of("properties",Map.of("minecraft:climate_variant","cold")));
        assertTrue(candidates.containsAll(Set.of("minecraft:cow","minecraft:cow_baby","minecraft:cold_cow","minecraft:cold_cow_baby")));
        assertFalse(names("cow",false,Map.of()).contains("minecraft:cow_baby"));
        assertFalse(names("cow",false,Map.of("properties",Map.of())).contains("minecraft:cold_cow"));
        assertTrue(names("armor_stand",true,Map.of()).contains("minecraft:armor_stand_small"));
        assertFalse(names("armor_stand",false,Map.of()).contains("minecraft:armor_stand_small"));
        assertTrue(names("sheep",true,Map.of()).contains("minecraft:sheep_baby_wool"));
        assertFalse(names("sheep",true,Map.of("is_sheared",1)).contains("minecraft:sheep_wool"));
        assertTrue(names("horse",true,Map.of("is_saddled",1,"has_body_equipment",true))
            .containsAll(Set.of("minecraft:horse_baby_saddle","minecraft:horse_baby_armor")));
        assertFalse(names("creeper",false,Map.of()).contains("minecraft:creeper_charge"));
        assertTrue(names("creeper",false,Map.of("is_powered",1)).contains("minecraft:creeper_charge"));
    }
    @Test void vehicleFallbacksDoNotLeakAcrossSpeciesOrNamespaces() {
        assertTrue(names("oak_boat",false,Map.of()).contains("minecraft:boat"));
        assertFalse(names("oak_boat",false,Map.of()).contains("minecraft:chest_boat"));
        assertTrue(names("bamboo_chest_raft",false,Map.of()).contains("minecraft:chest_raft"));
        assertTrue(names("hopper_minecart",false,Map.of()).contains("minecraft:minecart"));
        assertEquals(Set.of("example:oak_boat"),EntityModelNames.candidates("example:oak_boat",true,Map.of()));
    }
    @Test void priorityHonorsResolvedNamesAndStillPreservesUnrelatedEntities() {
        var priority=new EntityPackPriority("native",Map.of("native",1,"java",2),Map.of(
            "minecraft:emf/cem/armor_stand_small2.jem","java",
            "minecraft:optifine/cem/puffer_fish_big.properties","java",
            "minecraft:optifine/cem/dragon/dragon3.jem","java"));
        assertTrue(priority.owns("dynamic:stand.png",names("armor_stand",false,Map.of())));
        assertFalse(priority.owns("dynamic:stand.png",names("armor_stand",true,Map.of())));
        assertTrue(priority.owns("dynamic:fish.png",names("pufferfish",false,Map.of("puff_state",0))));
        assertFalse(priority.owns("dynamic:fish.png",names("pufferfish",false,Map.of("puff_state",2))));
        assertFalse(priority.owns("dynamic:dragon.png",names("ender_dragon",false,Map.of())));
        assertTrue(priority.owns("dynamic:cow.png",names("cow",false,Map.of())));
    }
}
