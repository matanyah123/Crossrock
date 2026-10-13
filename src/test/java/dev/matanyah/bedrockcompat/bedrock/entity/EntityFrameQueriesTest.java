package dev.matanyah.bedrockcompat.bedrock.entity;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EntityFrameQueriesTest {
    @Test void legacyBlockNamesSelectPropsWithoutChangingJavaIdentifiers() {
        for(var entry:Map.of("cobweb","web","magma_block","magma","jack_o_lantern","lit_pumpkin",
                "rooted_dirt","dirt_with_roots","note_block","noteblock").entrySet()) {
            var q=new EntityFrameQueries(new EntityQueryContext("§§","",Set.of(),Map.of()),Map.of(
                "slot.weapon.mainhand",new EntityFrameQueries.Item("minecraft:"+entry.getKey(),Set.of())),0,0,"fancy");
            assertEquals(1d,q.get("is_item_name_any",List.of("slot.weapon.mainhand","minecraft:"+entry.getValue())));
            assertEquals(1d,q.get("is_item_name_any",List.of("slot.weapon.mainhand","minecraft:"+entry.getKey())));
            assertEquals(entry.getValue(),q.get("get_equipped_item_name",List.of()));
            assertFalse(BedrockItemIds.matches("example:"+entry.getKey(),"minecraft:"+entry.getValue()));
        }
    }
    @Test void armorSlotsUseBedrockOrderAndMissingSlotsAreEmpty() {
        var q=new EntityFrameQueries(new EntityQueryContext("","",Set.of(),Map.of()),Map.of(
            "slot.armor.head",new EntityFrameQueries.Item("minecraft:netherite_helmet",Set.of()),
            "slot.armor.feet",new EntityFrameQueries.Item("minecraft:diamond_boots",Set.of())),0,0,"fancy");
        assertEquals(1d,q.get("has_armor_slot",List.of(0d)));
        assertEquals(0d,q.get("has_armor_slot",List.of(1d)));
        assertEquals(0d,q.get("has_armor_slot",List.of(2d)));
        assertEquals(1d,q.get("has_armor_slot",List.of(3d)));
    }
    private EntityFrameQueries queries() {
        return new EntityFrameQueries(new EntityQueryContext("Prop","minecraft:jungle",Set.of(),Map.of()),Map.of(
            "slot.weapon.mainhand",new EntityFrameQueries.Item("minecraft:stick",Set.of("minecraft:planks")),
            "slot.weapon.offhand",new EntityFrameQueries.Item("minecraft:air",Set.of()),
            "slot.armor.legs",new EntityFrameQueries.Item("minecraft:air",Set.of())),10,20,"fancy");
    }
    @Test void equipmentNameQueriesSupportOptionalSlotIndexAndEmptyString() {
        var q=queries();
        assertEquals(1d,q.get("is_item_name_any",List.of("slot.weapon.mainhand","minecraft:stick")));
        assertEquals(1d,q.get("is_item_name_any",List.of("slot.weapon.mainhand",0d,"minecraft:stick")));
        assertEquals(1d,q.get("is_item_name_any",List.of("slot.armor.legs",0d,"")));
        assertEquals("stick",q.get("get_equipped_item_name",List.of()));
        assertEquals("",q.get("get_equipped_item_name",List.of("off_hand")));
        assertEquals(1d,q.get("is_item_equipped",List.of("main_hand")));
        assertEquals(0d,q.get("is_item_equipped",List.of(1d)));
    }
    @Test void cameraAndTagsUseFrameSnapshots() {
        var q=queries();
        assertEquals(10d,q.get("camera_rotation",List.of(0d)));
        assertEquals(20d,q.get("camera_rotation",List.of(1d)));
        assertEquals(1d,q.get("equipped_item_any_tag",List.of("slot.weapon.mainhand","minecraft:planks")));
        assertEquals(1d,q.get("graphics_mode_is_any",List.of("simple","fancy")));
        assertThrows(IllegalArgumentException.class,()->q.get("is_item_equipped",List.of("wrong")));
    }
}
