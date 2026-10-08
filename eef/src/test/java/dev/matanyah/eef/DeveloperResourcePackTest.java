package dev.matanyah.eef;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeveloperResourcePackTest {
	@Test void developerPackParsesAndCoversEveryCurrentCapture() throws Exception {
		Path rulesPath = Path.of("dev-test-resource-pack/assets/eef_test/eef/captured_events.json");
		EefRuleSet set = EefRuleParser.parse(Files.readString(rulesPath));
		Set<EntityEventKind> expected = EnumSet.of(
			EntityEventKind.WORLD_LOADED, EntityEventKind.PLAYER_JOINED,
			EntityEventKind.ENTITY_LOADED, EntityEventKind.ENTITY_OBSERVED, EntityEventKind.ENTITY_STATE_CHANGED, EntityEventKind.ENTITY_ACTIVITY_CHANGED, EntityEventKind.ENTITY_DAMAGED,
			EntityEventKind.ENTITY_DIED, EntityEventKind.ENTITY_REMOVED,
			EntityEventKind.PLAYER_MOVED, EntityEventKind.PLAYER_JUMPED,
			EntityEventKind.PLAYER_STARTED_FLYING, EntityEventKind.PLAYER_STOPPED_FLYING,
			EntityEventKind.PLAYER_STARTED_SNEAKING, EntityEventKind.PLAYER_STOPPED_SNEAKING,
			EntityEventKind.PLAYER_STARTED_SWIMMING, EntityEventKind.PLAYER_STOPPED_SWIMMING,
			EntityEventKind.RIDING_STARTED, EntityEventKind.RIDING_STOPPED,
			EntityEventKind.TIME_CHANGED, EntityEventKind.WEATHER_CHANGED,
			EntityEventKind.PLAYER_GAME_MODE_CHANGED, EntityEventKind.WORLD_DIFFICULTY_CHANGED,
			EntityEventKind.ENTITY_ENTERED_RANGE, EntityEventKind.ENTITY_EXITED_RANGE,
			EntityEventKind.ENTITY_INTERACTED, EntityEventKind.PLAYER_BREAKING_BLOCK,
			EntityEventKind.PLAYER_INTERACTED_WITH_BLOCK, EntityEventKind.ITEM_USE_ON_BLOCK_STARTED,
			EntityEventKind.ITEM_USED, EntityEventKind.BLOCK_BROKEN, EntityEventKind.BLOCK_PLACED, EntityEventKind.BLOCK_STATE_CHANGED,
			EntityEventKind.TRADE_ATTEMPTED, EntityEventKind.TRADE_SCREEN_OPENED, EntityEventKind.TRADE_COMPLETED, EntityEventKind.TRADE_SCREEN_CLOSED);
		expected.addAll(Set.of(EntityEventKind.ENTITY_SPAWNED, EntityEventKind.ENTITY_TRANSFORMED,
			EntityEventKind.ITEM_PICKED_UP, EntityEventKind.PROJECTILE_HIT_ENTITY));
		Set<EntityEventKind> actual = EnumSet.noneOf(EntityEventKind.class);
		set.rules().forEach(rule -> actual.add(rule.event()));
		assertEquals(expected, actual);
		assertTrue(set.rules().stream().flatMap(rule -> rule.actions().stream())
			.anyMatch(action -> action.type().equals("extension_action_for_ignore_test")));
		assertEquals(57, set.rules().size());
		assertTrue(set.rules().stream().anyMatch(rule -> !rule.subjects().isEmpty()));
	}

	@Test void disabledMalformedFixtureIsRejectedWhenExplicitlyParsed() throws Exception {
		Path fixture = Path.of("dev-test-resource-pack/fixtures/malformed-rule.json.disabled");
		assertThrows(RuntimeException.class, () -> EefRuleParser.parse(Files.readString(fixture)));
	}

	@Test void generatedCreatureRulesPrefilterBeforeNearbyEntityQueries() throws Exception {
		Path rulesPath = Path.of("../villager-news-java/eef-generated-pack/assets/villager_news/eef/villager_news.json");
		EefRuleSet set = EefRuleParser.parse(Files.readString(rulesPath));
		java.util.concurrent.atomic.AtomicInteger resolutions = new java.util.concurrent.atomic.AtomicInteger();
		EefRuleEngine engine = new EefRuleEngine();
		engine.setSubjectResolver((query,event) -> { resolutions.incrementAndGet(); return java.util.List.of(); });
		engine.reload(set);
		engine.onEntityEvent(new EntityEvent(1, EntityEventKind.ENTITY_OBSERVED, null, null, null, null,
			EventReliability.CLIENT_OBSERVED, java.util.Map.of("self", java.util.Map.of(
				"id","item","type","minecraft:item","mob",false,"passenger_count",0))));
		assertEquals(0, resolutions.get(), "irrelevant observed entities must not scan for villager witnesses");
		engine.onEntityEvent(new EntityEvent(2, EntityEventKind.ENTITY_OBSERVED, null, null, null, null,
			EventReliability.CLIENT_OBSERVED, java.util.Map.of("self", java.util.Map.of(
				"id","pig","type","minecraft:pig","mob",true,"baby",false,"passenger_count",0))));
		assertEquals(1, resolutions.get(), "all nonmatching creature rules should be rejected before the one pig query");
	}

	@Test void generatedVillagerNewsVerticalSlicesUseGenericRules() throws Exception {
		Path rulesPath = Path.of("../villager-news-java/eef-generated-pack/assets/villager_news/eef/villager_news.json");
		EefRuleSet set = EefRuleParser.parse(Files.readString(rulesPath));
		assertEquals(478, set.rules().size());
		assertEquals(240, set.rules().stream().filter(rule -> !rule.subjects().isEmpty()).count());
		assertTrue(set.rules().stream().flatMap(rule -> rule.subjects().stream())
			.allMatch(query -> query.query().equals("nearby_entities")));
		EntityEvent playerDamage = new EntityEvent(10, EntityEventKind.ENTITY_DAMAGED, null, null, null, null,
			EventReliability.SERVER_REQUIRED, java.util.Map.of(
				"self", java.util.Map.of("id", "v", "type", "minecraft:villager", "baby", false),
				"attacker", java.util.Map.of("id", "p", "type", "minecraft:player"),
				"damage", java.util.Map.of("direct", true, "authoritative", true)));
		assertEquals(java.util.List.of("villager_attacked_directly_by_player"), set.rules().stream()
			.filter(rule -> rule.event() == EntityEventKind.ENTITY_DAMAGED && EefRuleEngine.matches(rule, playerDamage))
			.map(EefRule::id).toList());
		assertEquals(151, set.rules().stream().filter(rule -> rule.activation().mode() == EefActivation.Mode.ENTERED).count());
		assertTrue(set.rules().stream().filter(rule -> rule.event() == EntityEventKind.ENTITY_OBSERVED
			|| rule.event() == EntityEventKind.PLAYER_MOVED).allMatch(rule ->
			rule.activation().mode() != EefActivation.Mode.MATCH || rule.subjects().stream().anyMatch(query ->
				query.retrigger() == EefSubjectQuery.Retrigger.AFTER_EXIT)),
			"continuous polling rules must use an activation edge or after-exit encounter latch");
		for (String id : java.util.List.of("remaining_ajexrq", "remaining_qxgbwi", "remaining_zvbnea")) {
			EefRule movementRule = set.rules().stream().filter(rule -> rule.id().equals(id)).findFirst().orElseThrow();
			assertTrue(movementRule.subjects().stream().allMatch(query -> query.retrigger() == EefSubjectQuery.Retrigger.AFTER_EXIT));
		}
		EntityEvent farmerWorking = new EntityEvent(21, EntityEventKind.ENTITY_OBSERVED, null, null, null, null,
			EventReliability.CLIENT_OBSERVED, java.util.Map.of("self", java.util.Map.of(
				"id", "farmer", "type", "minecraft:villager", "baby", false,
				"profession", "minecraft:farmer", "ai", java.util.Map.of("primary_activity", "work", "behaviors", java.util.List.of()))));
		assertTrue(set.rules().stream().anyMatch(rule -> rule.id().contains("farmer_at_work") && EefRuleEngine.matches(rule, farmerWorking)));
		EntityEvent completedTrade = new EntityEvent(22, EntityEventKind.TRADE_COMPLETED, null, null, null, null,
			EventReliability.SERVER_REQUIRED, java.util.Map.of("self", java.util.Map.of("id", "merchant", "type", "minecraft:villager", "baby", false),
				"trade", java.util.Map.of("result", java.util.Map.of("type", "minecraft:bread", "count", 6), "session_count", 1)));
		assertTrue(set.rules().stream().anyMatch(rule -> rule.id().equals("trade_xmkwxd_complete_a_trade") && EefRuleEngine.matches(rule, completedTrade)));
		EntityEvent lowReputation = new EntityEvent(23, EntityEventKind.TRADE_SCREEN_OPENED, null, null, null, null,
			EventReliability.SERVER_REQUIRED, java.util.Map.of(
				"self", java.util.Map.of("id", "merchant", "type", "minecraft:villager", "baby", false, "sleeping", false),
				"reputation", java.util.Map.of("score", -100, "tier", "low")));
		assertEquals(java.util.List.of("trade_qmdvft_start_trading_with_low_reputation"), set.rules().stream()
			.filter(rule -> rule.event() == EntityEventKind.TRADE_SCREEN_OPENED && EefRuleEngine.matches(rule, lowReputation))
			.map(EefRule::id).toList());
		EntityEvent mayorTrade = new EntityEvent(23, EntityEventKind.TRADE_SCREEN_OPENED, null, null, null, null,
			EventReliability.SERVER_REQUIRED, java.util.Map.of("self", java.util.Map.of(
				"id", "mayor", "type", "minecraft:villager", "custom_name", "Mayor Villager", "sleeping", false)));
		assertEquals(java.util.List.of("special_mayor_njyapy"), set.rules().stream()
			.filter(rule -> rule.event() == EntityEventKind.TRADE_SCREEN_OPENED && EefRuleEngine.matches(rule, mayorTrade))
			.map(EefRule::id).toList());
		EntityEvent babyHit = new EntityEvent(23, EntityEventKind.ENTITY_DAMAGED, null, null, null, null,
			EventReliability.SERVER_REQUIRED, java.util.Map.of(
				"self", java.util.Map.of("id", "baby", "type", "minecraft:villager", "baby", true, "sleeping", false),
				"attacker", java.util.Map.of("id", "player", "type", "minecraft:player"),
				"damage", java.util.Map.of("direct", true, "authoritative", true)));
		assertEquals(java.util.List.of("baby_ahcvzd_player_hurts_a_baby_villager"), set.rules().stream()
			.filter(rule -> rule.event() == EntityEventKind.ENTITY_DAMAGED && EefRuleEngine.matches(rule, babyHit))
			.map(EefRule::id).toList());
		EntityEvent babyCalms = new EntityEvent(24, EntityEventKind.ENTITY_ACTIVITY_CHANGED, null, null, null, null,
			EventReliability.SERVER_REQUIRED, java.util.Map.of(
				"self", java.util.Map.of("id", "baby", "type", "minecraft:villager", "baby", true, "sleeping", false),
				"activity", java.util.Map.of("before", java.util.Map.of("primary_activity", "panic"),
					"after", java.util.Map.of("primary_activity", "idle"))));
		assertEquals(java.util.List.of("baby_wsxfok_calm_down_after_a_scare"), set.rules().stream()
			.filter(rule -> rule.event() == EntityEventKind.ENTITY_ACTIVITY_CHANGED && EefRuleEngine.matches(rule, babyCalms))
			.map(EefRule::id).toList());
		assertTrue(set.rules().stream().filter(rule -> rule.id().startsWith("activity_")
			&& !rule.id().startsWith("activity_rfnirh_")).allMatch(rule -> rule.conditions().stream().anyMatch(condition ->
				condition.path().equals("self.baby") && condition.operator() == EefCondition.Operator.EQ
				&& Boolean.FALSE.equals(condition.value()))));
		EntityEvent raidRefusal = new EntityEvent(24, EntityEventKind.TRADE_ATTEMPTED, null, null, null, null,
			EventReliability.SERVER_REQUIRED, java.util.Map.of(
				"self", java.util.Map.of("id", "merchant", "type", "minecraft:villager", "baby", false, "sleeping", false),
				"trade_attempt", java.util.Map.of("refusal_reason", "raid")));
		assertEquals(java.util.List.of("trade_klabhl_refuses_to_trade_during_a_raid"), set.rules().stream()
			.filter(rule -> rule.event() == EntityEventKind.TRADE_ATTEMPTED && EefRuleEngine.matches(rule, raidRefusal))
			.map(EefRule::id).toList());
		java.util.Set<String> tradingDialogues = java.util.Set.of("vlrsrn", "xduuwm", "kuhvdv", "qmdvft", "clbjww",
			"zalmof", "lhdgsy", "klabhl", "nukxsf", "nlbhku", "czvvwy", "lilimm", "laztau", "xmkwxd",
			"kxoqky", "uzdvsi", "bvrbhy", "dbzjqi", "erbcfn", "yubpbb", "hxlyuc", "jkeahu", "myajyt", "vggdrt", "stqafd");
		java.util.Set<String> emittedTrading = set.rules().stream().filter(rule -> rule.id().startsWith("trade_"))
			.flatMap(rule -> rule.outcomes().stream()).flatMap(outcome -> outcome.actions().stream())
			.filter(action -> action.type().equals("play_sound"))
			.map(action -> String.valueOf(action.parameters().get("sound")).split("\\.")[1])
			.collect(java.util.stream.Collectors.toSet());
		assertEquals(tradingDialogues, emittedTrading, "every Trading dialogue group must be reachable through generic EEF rules");
		java.util.List<EefAction> sounds = set.rules().stream().flatMap(rule -> rule.outcomes().stream())
			.flatMap(outcome -> outcome.actions().stream()).filter(action -> action.type().equals("play_sound")).toList();
		assertFalse(sounds.isEmpty());
		assertTrue(sounds.stream().allMatch(action -> "dialogue".equals(action.parameters().get("channel"))
			&& "entity".equals(action.parameters().get("channel_scope"))
			&& Boolean.TRUE.equals(action.parameters().get("follow_entity"))
			&& action.parameters().get("duration_ticks") instanceof Number duration
			&& duration.longValue() > 0 && duration.longValue() < 1200));
		assertTrue(set.rules().stream().allMatch(rule -> rule.cooldowns().stream().anyMatch(cooldown ->
			cooldown.scope() == EefCooldown.Scope.RULE_ENTITY && cooldown.ticks() ==
				(rule.id().contains("kljgyu") || rule.id().contains("nxalcz") ? 48 : 1))));
		assertTrue(set.rules().stream().allMatch(rule -> rule.defaultFrequency() >= 1 && rule.defaultFrequency() <= 10));
		assertEquals(java.util.Set.of("remaining_wrswgiswxeva", "remaining_gmrypk_both_employed",
			"remaining_gmrypk_neither_employed", "remaining_gmrypk_mixed_employment"), set.rules().stream()
			.filter(rule -> !rule.defaultImportant()).map(EefRule::id).collect(java.util.stream.Collectors.toSet()));
		EefRule genericBreak = set.rules().stream().filter(rule -> rule.id().equals("nearby_villager_comments_on_broken_block")).findFirst().orElseThrow();
		assertTrue(genericBreak.conditions().stream().anyMatch(condition -> condition.path().equals("previous_block.replaceable")
			&& condition.operator() == EefCondition.Operator.EQ && Boolean.FALSE.equals(condition.value())),
			"replaceable fire and short grass must not count as generic block breaking");
		assertTrue(set.rules().stream().anyMatch(rule -> rule.id().equals("damage_rueszy_attacked_with_a_sword")));
		assertTrue(set.rules().stream().anyMatch(rule -> rule.id().equals("time_rclyrl_christmas_day")));
		assertTrue(set.rules().stream().anyMatch(rule -> rule.id().equals("life_rlfjux_receive_beetroot")));
		assertTrue(set.rules().stream().anyMatch(rule -> rule.id().equals("player_arzojk_sets_hard")));
		EntityEvent dinnerbone = new EntityEvent(50, EntityEventKind.ENTITY_STATE_CHANGED, null, null, null, null,
			EventReliability.CLIENT_OBSERVED, java.util.Map.of(
				"self", java.util.Map.of("id", "villager", "type", "minecraft:villager", "baby", false,
					"custom_name", "Dinnerbone", "sleeping", false),
				"change", java.util.Map.of("path", "custom_name", "after", "Dinnerbone")));
		assertEquals(java.util.List.of("life_qmpcxi_name_a_villager_dinnerbone"), set.rules().stream()
			.filter(rule -> rule.event() == EntityEventKind.ENTITY_STATE_CHANGED && EefRuleEngine.matches(rule, dinnerbone))
			.map(EefRule::id).toList());
		assertTrue(set.rules().stream().filter(rule -> rule.id().equals("player_hzjycq_dies")).findFirst().orElseThrow()
			.reliability().contains(EventReliability.SERVER_REQUIRED));
		EntityEvent woolyInteraction = new EntityEvent(51, EntityEventKind.ENTITY_INTERACTED, null, null, null, null,
			EventReliability.CLIENT_OBSERVED, java.util.Map.of(
				"self", java.util.Map.of("id", "wooly", "type", "minecraft:sheep",
					"custom_name", "Wooly the Sheep", "sleeping", false),
				"interaction", java.util.Map.of("action", "use"),
				"item", java.util.Map.of("type", "minecraft:air")));
		assertEquals(java.util.List.of("special_wooly_fskcce"), set.rules().stream()
			.filter(rule -> rule.event() == EntityEventKind.ENTITY_INTERACTED && EefRuleEngine.matches(rule, woolyInteraction))
			.map(EefRule::id).toList());
		EntityEvent woolyWanders = new EntityEvent(52, EntityEventKind.ENTITY_OBSERVED, null, null, null, null,
			EventReliability.SERVER_REQUIRED, java.util.Map.of(
				"self", java.util.Map.of("id", "wooly", "type", "minecraft:sheep", "custom_name", "WOOLY",
					"sleeping", false, "ai", java.util.Map.of("primary_activity", "none", "navigating", true)),
				"distance", java.util.Map.of("observer_to_self", 13.0)));
		assertEquals(java.util.List.of("special_wooly_vmohcm"), set.rules().stream()
			.filter(rule -> rule.event() == EntityEventKind.ENTITY_OBSERVED && EefRuleEngine.matches(rule, woolyWanders))
			.map(EefRule::id).toList());
		EefRule fullIron = set.rules().stream().filter(rule -> rule.id().equals("player_wears_full_iron_armor")).findFirst().orElseThrow();
		assertTrue(fullIron.conditions().stream().anyMatch(condition -> condition.path().equals("target.armor_count")
			&& condition.operator() == EefCondition.Operator.EQ && condition.value() instanceof Number count && count.intValue() == 4));
		assertTrue(fullIron.conditions().stream().anyMatch(condition -> condition.path().equals("target.full_armor_material")
			&& condition.operator() == EefCondition.Operator.EQ && "iron".equals(condition.value())));
		EefRule mixedArmor = set.rules().stream().filter(rule -> rule.id().equals("player_wears_mixed_armor")).findFirst().orElseThrow();
		assertTrue(mixedArmor.conditions().stream().anyMatch(condition -> condition.path().equals("target.armor_mixed")
			&& condition.operator() == EefCondition.Operator.EQ && Boolean.TRUE.equals(condition.value())));
		EefRule sleeping = set.rules().stream().filter(rule -> rule.id().equals("villager_speaks_while_sleeping")).findFirst().orElseThrow();
		assertTrue(sleeping.conditions().stream().anyMatch(condition -> condition.path().equals("self.sleeping")
			&& condition.operator() == EefCondition.Operator.EQ && Boolean.TRUE.equals(condition.value())));
		assertEquals(1, sleeping.defaultFrequency());
		assertTrue(sleeping.outcomes().stream().flatMap(outcome -> outcome.actions().stream())
			.filter(action -> action.type().equals("play_sound"))
			.allMatch(action -> String.valueOf(action.parameters().get("sound")).startsWith("villager_news:dialogue.asqzby.")));
		assertTrue(set.rules().stream().filter(rule -> !rule.id().equals("villager_goes_to_bed")
			&& !rule.id().equals("villager_speaks_while_sleeping")).allMatch(rule -> rule.conditions().stream().anyMatch(condition ->
			condition.path().equals("self.sleeping") && condition.operator() == EefCondition.Operator.NE && Boolean.TRUE.equals(condition.value()))));
		EntityEvent creative = new EntityEvent(20, EntityEventKind.PLAYER_GAME_MODE_CHANGED, null, null, null, null,
			EventReliability.CLIENT_OBSERVED, java.util.Map.of(
				"self", java.util.Map.of("id","v","type","minecraft:villager"),
				"target", java.util.Map.of("id","p","type","minecraft:player"),
				"player", java.util.Map.of("game_mode","creative")));
		assertEquals(java.util.List.of("player_switches_to_creative"), set.rules().stream()
			.filter(rule -> rule.event() == EntityEventKind.PLAYER_GAME_MODE_CHANGED && EefRuleEngine.matches(rule, creative))
			.map(EefRule::id).toList());

		java.util.Map<String, Object> relationships = java.util.Map.of(
			"self_to_target", java.util.Map.of("same_entity", false, "distance", 4.0, "line_of_sight", true),
			"target_to_observer", java.util.Map.of("from_targets_to", false));
		EntityEvent tamedBabyWolf = observed("minecraft:wolf", true, true, false, 0, relationships);
		assertEquals(java.util.List.of("nearby_villager_notices_tamed_baby_wolf"), matchingCreatureRules(set, tamedBabyWolf));

		EntityEvent skeletonJockey = observed("minecraft:skeleton", false, false, false, 1, relationships);
		assertEquals(java.util.List.of("nearby_villager_notices_jockey"), matchingCreatureRules(set, skeletonJockey));

		EntityEvent unknownMob = observed("minecraft:breeze", false, false, false, 0, relationships);
		assertEquals(java.util.List.of("nearby_villager_notices_unknown_mob"), matchingCreatureRules(set, unknownMob));

		java.util.Map<String, Object> targetingRelationships = java.util.Map.of(
			"self_to_target", java.util.Map.of("same_entity", false, "distance", 4.0, "line_of_sight", true),
			"target_to_observer", java.util.Map.of("from_targets_to", true));
		EntityEvent hostileIronGolem = observed("minecraft:iron_golem", false, false, false, 0, targetingRelationships);
		assertEquals(java.util.List.of("nearby_villager_notices_iron_golem_targeting_player"), matchingCreatureRules(set, hostileIronGolem));

		java.util.List<EefAction> fired = new java.util.ArrayList<>();
		EefRuleEngine blockEngine = new EefRuleEngine((action, event) -> fired.add(action));
		blockEngine.setSubjectResolver((query, event) -> java.util.List.of(new EntityEvent(
			event.gameTick(), event.kind(), null, null, event.position(), event.detail(), event.reliability(),
			java.util.Map.of(
				"self", java.util.Map.of("id", "speaker", "type", "minecraft:villager", "baby", false),
				"actor", java.util.Map.of("id", "player", "type", "minecraft:player", "sneaking", false),
				"block", java.util.Map.of("type", "minecraft:melon", "air", false),
				"previous_block", java.util.Map.of("type", "minecraft:air", "air", true)))));
		blockEngine.reload(set);
		blockEngine.setRulePreferences(new EefRulePreferences() { @Override public int frequency(EefRule rule) { return 1; } });
		blockEngine.onEntityEvent(new EntityEvent(40, EntityEventKind.BLOCK_PLACED, null, null, null, null,
			EventReliability.CLIENT_INFERRED, java.util.Map.of(
				"self", java.util.Map.of("id", "player", "type", "minecraft:player"),
				"actor", java.util.Map.of("id", "player", "type", "minecraft:player", "sneaking", false),
				"block", java.util.Map.of("type", "minecraft:melon", "air", false),
				"previous_block", java.util.Map.of("type", "minecraft:air", "air", true))));
		blockEngine.tick(2_000);
		assertEquals(1, fired.stream().filter(action -> action.type().equals("play_sound")).count(),
			"generic block fallback must not be queued after a specific reaction");
		assertTrue(fired.stream().filter(action -> action.type().equals("play_sound"))
			.allMatch(action -> String.valueOf(action.parameters().get("sound")).startsWith("villager_news:dialogue.veaotb.")));
	}

	private static EntityEvent observed(String type, boolean baby, boolean tamed, boolean angry, int passengers,
		java.util.Map<String, Object> relationships) {
		return new EntityEvent(30, EntityEventKind.ENTITY_OBSERVED, null, null, null, null,
			EventReliability.CLIENT_OBSERVED, java.util.Map.of(
				"self", java.util.Map.of("id", "v", "type", "minecraft:villager"),
				"target", java.util.Map.of("id", "m", "type", type, "baby", baby, "tamed", tamed,
					"angry", angry, "mob", true, "passenger_count", passengers),
				"relationships", relationships));
	}

	private static java.util.List<String> matchingCreatureRules(EefRuleSet set, EntityEvent event) {
		return set.rules().stream()
			.filter(rule -> rule.event() == EntityEventKind.ENTITY_OBSERVED
				&& rule.id().startsWith("nearby_villager_notices_") && EefRuleEngine.matches(rule, event))
			.map(EefRule::id).toList();
	}
}
