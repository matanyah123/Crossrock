package dev.matanyah.bedrockcompat.bedrock.entity;

import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PackSettingQueryTest {
    @Test void evaluatesSelectionsWithoutPackSpecificNames() {
        var config = new BedrockPackConfiguration(Optional.empty(), Map.of(
            "demo:multi", List.of("rain", "snow"), "demo:single", "blue", "demo:toggle", true));
        var queries = new EntityQueryContext("", "", Set.of(), Map.of(), config);
        assertEquals(1d, queries.get("is_pack_setting_selected", List.of("demo:multi", "rain")));
        assertEquals(0d, queries.get("is_pack_setting_selected", List.of("demo:multi", "wind")));
        assertEquals(1d, queries.get("is_pack_setting_selected", List.of("demo:single", "blue")));
        assertEquals(0d, queries.get("is_pack_setting_selected", List.of("missing", "blue")));
    }
}
