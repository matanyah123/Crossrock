package dev.matanyah.bedrockcompat.bedrock.entity;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EntityEnvironmentQueriesTest {
    @Test void relativeBlocksFloorEntityPositionPlusOffsetAndPreserveAnyAllSemantics() {
        var queries=new EntityEnvironmentQueries((q,a)->42d,(x,y,z,tag)->
            x==1&&y==63&&z==-1&&Set.of("minecraft:logs","wood").contains(tag),.8,64.2,-.1);
        assertEquals(1d,queries.get("relative_block_has_any_tag",List.of(.5,-1d,0d,"unknown","wood")));
        assertEquals(1d,queries.get("relative_block_has_all_tags",List.of(.5,-1d,0d,"wood","minecraft:logs")));
        assertEquals(0d,queries.get("relative_block_has_all_tags",List.of(.5,-1d,0d,"wood","unknown")));
        assertEquals(42d,queries.get("health",List.of()));
    }
    @Test void farOrNonFiniteOffsetsCannotAccessWorld() {
        var queries=new EntityEnvironmentQueries((q,a)->0d,(x,y,z,tag)->{fail("Invalid query accessed world");return false;},0,0,0);
        assertThrows(IllegalArgumentException.class,()->queries.get("relative_block_has_any_tag",List.of(129d,0d,0d,"tag")));
        assertThrows(IllegalArgumentException.class,()->queries.get("relative_block_has_any_tag",List.of(Double.NaN,0d,0d,"tag")));
        assertThrows(IllegalArgumentException.class,()->queries.get("relative_block_has_all_tags",List.of(0d,0d,0d)));
    }
}
