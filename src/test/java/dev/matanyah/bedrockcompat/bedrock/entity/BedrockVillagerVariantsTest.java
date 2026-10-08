package dev.matanyah.bedrockcompat.bedrock.entity;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockVillagerVariantsTest {
    @Test void professionAndBiomeIdsMatchBedrockRatherThanJavaRegistryOrder() {
        String[] professions={"none","farmer","fisherman","shepherd","fletcher","librarian","cartographer","cleric",
            "armorer","weaponsmith","toolsmith","butcher","leatherworker","mason","nitwit"};
        for(int i=0;i<professions.length;i++)assertEquals(i,BedrockVillagerVariants.profession("minecraft:"+professions[i]));
        assertEquals(4,BedrockVillagerVariants.biome("snow"));
        assertThrows(IllegalArgumentException.class,()->BedrockVillagerVariants.profession("modded:unknown"));
    }
    @Test void cameraAimAnglesUseEntityPositionNotCameraEulerAngles() {
        var context=new EntityQueryContext("","",Set.of(),Map.of("camera_delta",List.of(1d,1d,0d)));
        assertEquals(-45d,(double)context.get("rotation_to_camera",List.of(0d)),1e-8);
        assertEquals(-90d,(double)context.get("rotation_to_camera",List.of(1d)),1e-8);
    }
}
