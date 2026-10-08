package dev.matanyah.eef.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EefClientContextTest {
	@Test void classifiesActualArmorMaterialsButExcludesElytraAndWearables() {
		assertEquals("iron", EefClientContext.armorMaterialForType("minecraft:iron_chestplate").orElseThrow());
		assertEquals("netherite", EefClientContext.armorMaterialForType("minecraft:netherite_boots").orElseThrow());
		assertEquals("turtle", EefClientContext.armorMaterialForType("minecraft:turtle_helmet").orElseThrow());
		assertTrue(EefClientContext.armorMaterialForType("minecraft:elytra").isEmpty());
		assertTrue(EefClientContext.armorMaterialForType("minecraft:carved_pumpkin").isEmpty());
		assertTrue(EefClientContext.armorMaterialForType("minecraft:player_head").isEmpty());
	}

	@Test void computesInventoryAdditions() {
		assertEquals(java.util.Map.of("minecraft:bread", 2, "minecraft:carrot", 1),
			EefActivityClientState.delta(java.util.Map.of("minecraft:bread", 1),
				java.util.Map.of("minecraft:bread", 3, "minecraft:carrot", 1)));
	}
}
