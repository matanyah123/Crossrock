package dev.matanyah.bedrockcompat.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArmorStandDisplayModeTest {
    @Test void exactControlNamesDoNotAffectNormalNames() {
        assertTrue(ArmorStandDisplayMode.fromName("§§").hidesName());
        assertTrue(ArmorStandDisplayMode.fromName("§§§§").hidesName());
        assertEquals(0,ArmorStandDisplayMode.fromName("§§§§").originY(),"Control names must not introduce a world-space height offset");
        assertEquals(0,ArmorStandDisplayMode.fromName("§§").originY());
        for(String name:new String[]{"","§§§","§§ player","Player"}) {
            assertFalse(ArmorStandDisplayMode.fromName(name).hidesName());
            assertEquals(0,ArmorStandDisplayMode.fromName(name).originY());
        }
    }
}
