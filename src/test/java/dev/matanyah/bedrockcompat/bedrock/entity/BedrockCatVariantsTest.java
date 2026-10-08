package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedrockCatVariantsTest {
    @Test void allVanillaCoatsUseBedrockRatherThanJavaOrder() {
        var coats=List.of("white","black","red","siamese","british_shorthair","calico","persian","ragdoll","tabby","all_black","jellie");
        for(int i=0;i<coats.size();i++) {
            assertEquals(i,BedrockCatVariants.variant(coats.get(i)));
            assertEquals(i,BedrockCatVariants.variant("minecraft:"+coats.get(i)));
        }
    }
    @Test void unknownModdedCoatsAreNotSilentlyTurnedIntoWhiteCats() {
        assertThrows(IllegalArgumentException.class,()->BedrockCatVariants.variant("example:white"));
        assertThrows(IllegalArgumentException.class,()->BedrockCatVariants.variant("minecraft:unknown"));
    }
}
