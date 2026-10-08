package dev.matanyah.bedrockcompat.bedrock.entity;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EntityPackPriorityTest {
    private static final String TEXTURE="minecraft:textures/entity/armorstand/armorstand.png";
    @Test void randomizedEtfPropertyRetainsOriginalTexturePriority() {
        var priority=new EntityPackPriority("native",Map.of("native",1,"java",2),
            Map.of("minecraft:optifine/random/entity/cow/cow.properties","java"));
        assertFalse(priority.owns("minecraft:cow","minecraft:optifine/random/entity/cow/cow3.png"));
        assertFalse(priority.owns("minecraft:cow","minecraft:textures/entity/cow/cow.png"));
        assertTrue(priority.owns("minecraft:pig","minecraft:textures/entity/pig/pig.png"));
    }
    @Test void disabledNativePackDoesNotTakeOver() {
        assertFalse(new EntityPackPriority("native",Map.of("vanilla",0),Map.of()).owns("minecraft:armor_stand",TEXTURE));
    }
    @Test void higherTextureDefersButLowerTextureDoesNot() {
        var ranks=Map.of("vanilla",0,"native",1,"java",2);
        assertFalse(new EntityPackPriority("native",ranks,Map.of(TEXTURE,"java")).owns("minecraft:armor_stand",TEXTURE));
        assertTrue(new EntityPackPriority("native",ranks,Map.of(TEXTURE,"vanilla")).owns("minecraft:armor_stand",TEXTURE));
    }
    @Test void higherEmfVariantsAffectOnlyTheirEntity() {
        var priority=new EntityPackPriority("native",Map.of("native",1,"java",2),
            Map.of("minecraft:optifine/cem/armor_stand2.jem","java"));
        assertFalse(priority.owns("minecraft:armor_stand",TEXTURE));
        assertTrue(priority.owns("minecraft:cow","minecraft:textures/entity/cow/cow.png"));
    }
    @Test void nativeAboveJavaModelWinsAndUnknownTextureDoesNotThrow() {
        var priority=new EntityPackPriority("native",Map.of("java",1,"native",2),
            Map.of("minecraft:emf/cem/armor_stand.properties","java"));
        assertTrue(priority.owns("minecraft:armor_stand","dynamic:entity/randomized.png"));
    }
}
