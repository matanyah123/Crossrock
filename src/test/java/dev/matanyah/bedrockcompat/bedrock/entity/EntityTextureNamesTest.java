package dev.matanyah.bedrockcompat.bedrock.entity;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EntityTextureNamesTest {
    @Test void nonLivingPathsMatchVanillaRendererAssets() {
        for(String entity:List.of("minecart","chest_minecart","furnace_minecart","hopper_minecart",
                "tnt_minecart","spawner_minecart","command_block_minecart"))
            assertEquals("minecraft:textures/entity/minecart/minecart.png",EntityTextureNames.nonLiving("minecraft:"+entity,false));
        assertEquals("minecraft:textures/entity/projectiles/arrow.png",EntityTextureNames.nonLiving("minecraft:arrow",false));
        assertEquals("minecraft:textures/entity/projectiles/arrow_tipped.png",EntityTextureNames.nonLiving("minecraft:arrow",true));
        assertEquals("minecraft:textures/entity/projectiles/arrow_spectral.png",EntityTextureNames.nonLiving("minecraft:spectral_arrow",true));
        assertEquals("minecraft:textures/entity/shulker/spark.png",EntityTextureNames.nonLiving("minecraft:shulker_bullet",false));
        assertEquals("minecraft:textures/entity/trident/trident.png",EntityTextureNames.nonLiving("minecraft:trident",false));
        assertEquals("minecraft:textures/entity/boat/bamboo.png",EntityTextureNames.nonLiving("minecraft:bamboo_raft",false));
        assertEquals("other:textures/entity/arrow.png",EntityTextureNames.nonLiving("other:arrow",true));
    }

    @Test void higherJavaTextureWinsOnlyForItsMatchingEntity() {
        var priority=new EntityPackPriority("native",Map.of("native",1,"java",2),Map.of(
            "minecraft:textures/entity/minecart/minecart.png","java",
            "minecraft:textures/entity/projectiles/arrow_tipped.png","java"));
        for(String entity:List.of("minecraft:minecart","minecraft:chest_minecart","minecraft:arrow"))
            assertFalse(priority.owns(EntityTextureNames.nonLiving(entity,true),Set.of(entity)));
        assertTrue(priority.owns(EntityTextureNames.nonLiving("minecraft:arrow",false),Set.of("minecraft:arrow")));
        assertTrue(priority.owns(EntityTextureNames.nonLiving("minecraft:spectral_arrow",false),Set.of("minecraft:spectral_arrow")));
    }
}
