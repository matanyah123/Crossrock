package dev.matanyah.bedrockcompat.bedrock.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockTextureCompositionTest {
    @Test void transparentBlackCutsOutAndTransparentColorPreservesBase() {
        assertEquals(0,BedrockTextureComposition.masked(0xff123456,0));
        assertEquals(0xff123456,BedrockTextureComposition.masked(0xff123456,0x00ffffff));
        assertEquals(0x40123456,BedrockTextureComposition.masked(0x40123456,0x00ff0000));
    }
    @Test void opaqueMaskReplacesAndFractionalNonblackMaskKeepsBase() {
        assertEquals(0xffaabbcc,BedrockTextureComposition.masked(0xff123456,0xffaabbcc));
        assertEquals(0xff123456,BedrockTextureComposition.masked(0xff123456,0x80aabbcc));
        assertEquals(0x80000000,BedrockTextureComposition.masked(0xff123456,0x80000000));
        assertEquals(0xff000000,BedrockTextureComposition.masked(0xff123456,0xff000000));
    }
    @Test void exactSamplingGridSupportsDifferentResolutionsWithinBudget() {
        assertEquals(64,BedrockTextureComposition.commonSize(16,64));
        assertEquals(60,BedrockTextureComposition.commonSize(12,20));
        assertEquals(60,BedrockTextureComposition.commonSize(20,12));
        assertThrows(IllegalArgumentException.class,()->BedrockTextureComposition.commonSize(4095,4096));
        assertThrows(IllegalArgumentException.class,()->BedrockTextureComposition.commonSize(0,16));
    }
}
