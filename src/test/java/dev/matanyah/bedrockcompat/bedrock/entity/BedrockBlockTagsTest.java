package dev.matanyah.bedrockcompat.bedrock.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockBlockTagsTest {
    @Test void mapsToolTagsWithoutRewritingPackDefinedTags() {
        assertEquals("minecraft:mineable/axe",BedrockBlockTags.javaTag("minecraft:is_axe_item_destructible"));
        assertEquals("minecraft:mineable/pickaxe",BedrockBlockTags.javaTag("minecraft:is_pickaxe_item_destructible"));
        assertEquals("pack:custom",BedrockBlockTags.javaTag("pack:custom"));
        assertEquals("minecraft:trapdoors",BedrockBlockTags.javaTag("minecraft:trapdoors"));
    }
}
