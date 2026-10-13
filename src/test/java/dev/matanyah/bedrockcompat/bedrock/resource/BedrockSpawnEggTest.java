package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.JsonParser;
import dev.matanyah.bedrockcompat.resource.InMemoryGeneratedResourceStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class BedrockSpawnEggTest {
    private static Map<String,byte[]> assets() {
        return Map.of("textures/item_texture.json",("{\"texture_data\":{\"egg\":{\"textures\":[\"textures/items/a\",\"textures/items/b\"]}}}").getBytes(StandardCharsets.UTF_8),
            "textures/items/a.png",new byte[]{1},"textures/items/b.png",new byte[]{2});
    }
    private static com.google.gson.JsonObject description(String egg) {
        return JsonParser.parseString("{\"identifier\":\"sample:actor\",\"spawn_egg\":"+egg+"}").getAsJsonObject();
    }
    @Test void indexedAtlasTextureReplacesJavaItemPresentationWithoutTint() throws Exception {
        var sink=new InMemoryGeneratedResourceStore();
        BedrockSpawnEgg.translate(description("{\"texture\":\"egg\",\"texture_index\":1}"),assets(),sink);
        assertArrayEquals(new byte[]{2},sink.snapshot().resources().get(Identifier.parse("sample:textures/item/actor_spawn_egg.png")));
        var item=new String(sink.snapshot().resources().get(Identifier.parse("sample:items/actor_spawn_egg.json")),StandardCharsets.UTF_8);
        assertTrue(item.contains("sample:item/actor_spawn_egg"));assertFalse(item.contains("tints"));
        assertEquals(3,sink.snapshot().resources().size());
    }
    @Test void omittedIndexDefaultsToZeroAndPriorityReplacesAllPresentationAssets() throws Exception {
        var sink=new InMemoryGeneratedResourceStore();
        BedrockSpawnEgg.translate(description("{\"texture\":\"egg\",\"texture_index\":1}"),assets(),sink);
        BedrockSpawnEgg.translate(description("{\"texture\":\"egg\"}"),assets(),sink);
        assertArrayEquals(new byte[]{1},sink.snapshot().resources().get(Identifier.parse("sample:textures/item/actor_spawn_egg.png")));
    }
    @Test void badAliasIndicesAndMissingImagesNeverPublishBrokenItems() {
        for(String egg:List.of("{\"texture\":\"absent\"}","{\"texture\":\"egg\",\"texture_index\":-1}","{\"texture\":\"egg\",\"texture_index\":2}")) {
            var sink=new InMemoryGeneratedResourceStore();
            assertThrows(IOException.class,()->BedrockSpawnEgg.translate(description(egg),assets(),sink));
            assertTrue(sink.snapshot().resources().isEmpty());
        }
        var missing=new HashMap<>(assets());missing.remove("textures/items/a.png");
        assertThrows(IOException.class,()->BedrockSpawnEgg.translate(description("{\"texture\":\"egg\"}"),missing,new InMemoryGeneratedResourceStore()));
    }
    @Test void colorOnlyEggsRemainExplicitlyUnsupportedNotIncorrectlyRecoloredJavaEggs() {
        var sink=new InMemoryGeneratedResourceStore();
        assertThrows(IOException.class,()->BedrockSpawnEgg.translate(description("{\"base_color\":\"#123456\",\"overlay_color\":\"#ffffff\"}"),assets(),sink));
        assertTrue(sink.snapshot().resources().isEmpty());
    }
    @Test void absentDeclarationPreservesVanilla() throws Exception {
        var sink=new InMemoryGeneratedResourceStore();
        BedrockSpawnEgg.translate(JsonParser.parseString("{\"identifier\":\"minecraft:pig\"}").getAsJsonObject(),Map.of(),sink);
        assertTrue(sink.snapshot().resources().isEmpty());
    }
}
