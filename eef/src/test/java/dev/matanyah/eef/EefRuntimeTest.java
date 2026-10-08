package dev.matanyah.eef;

import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EefRuntimeTest {
	@Test void evaluatesRichContextAndTypedConditions() {
		EefRule rule = rules("""
			{"event":"entity_damaged","conditions":[
			{"path":"self.type","operator":"eq","value":"minecraft:villager"},
			{"path":"self.health","operator":"lt","value":10},
			{"path":"self.tags","operator":"contains","value":"adult"},
			{"path":"target.type","operator":"not_exists"},
			{"path":"distance.observer_to_self","operator":"lte","value":24}]}
			""").getFirst();
		EntityEvent event = event(4, Map.of("self", Map.of("id","v1","type","minecraft:villager","health",7,"tags",List.of("adult")), "distance", Map.of("observer_to_self", 8)));
		assertEquals("minecraft:villager", event.value("self.type").orElseThrow());
		assertTrue(EefRuleEngine.matches(rule, event));
	}

	@Test void performsWeightedSelection() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine(new Random(4), (action,event) -> seen.add(action.type()));
		engine.reload(new EefRuleSet("test", 2, rules("""
			{"id":"weighted","event":"entity_damaged","outcomes":[
			{"id":"common","weight":9,"actions":[{"type":"common"}]},
			{"id":"rare","weight":1,"actions":[{"type":"rare"}]}]}
			""")));
		for (int i=0;i<1000;i++) engine.onEntityEvent(event(i, Map.of("self",Map.of("id","v"+i))));
		long common = seen.stream().filter("common"::equals).count();
		assertTrue(common > 830 && common < 950, "weighted common count=" + common);
	}

	@Test void enforcesRuleEntityCooldownAtTickBoundary() {
		List<Long> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> { if (action.type().equals("mark")) seen.add(event.gameTick()); });
		engine.reload(new EefRuleSet("test",2,rules("""
			{"id":"cool","event":"entity_damaged","cooldown":{"scope":"rule_entity","ticks":10},"actions":[{"type":"mark"}]}
			""")));
		engine.onEntityEvent(event(1, self("a"))); engine.onEntityEvent(event(5, self("a")));
		engine.onEntityEvent(event(5, self("b"))); engine.onEntityEvent(event(11, self("a")));
		assertEquals(List.of(1L,5L,11L), seen);
	}

	@Test void appliesPerTriggerFrequencyAndEnabledPreference() {
		List<Long> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> { if (action.type().equals("mark")) seen.add(event.gameTick()); });
		engine.setRulePreferences(new EefRulePreferences() {
			@Override public int frequency(EefRule rule) { return 3; }
		});
		engine.reload(new EefRuleSet("test", 2, rules("""
			{"id":"rare","event":"entity_damaged","actions":[{"type":"mark"}]}
			""")));
		for (int tick = 1; tick <= 6; tick++) engine.onEntityEvent(event(tick, self("a")));
		assertEquals(List.of(3L, 6L), seen);
		engine.setRulePreferences(new EefRulePreferences() { @Override public boolean enabled(EefRule rule) { return false; } });
		engine.onEntityEvent(event(7, self("a")));
		assertEquals(List.of(3L, 6L), seen);
	}

	@Test void nonImportantTriggerSkipsWhileSpeakerBusyWithoutConsumingOccurrence() {
		List<Long> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> { if (action.type().equals("mark")) seen.add(event.gameTick()); });
		engine.setRulePreferences(new EefRulePreferences() {
			@Override public boolean important(EefRule rule) { return false; }
		});
		engine.reload(new EefRuleSet("test", 2, rules("""
			{"id":"line","event":"entity_damaged","actions":[{"type":"play_sound","sound":"x:y","channel":"dialogue","channel_scope":"entity","duration_ticks":5},{"type":"mark"}]}
			""")));
		engine.onEntityEvent(event(1, self("a")));
		engine.onEntityEvent(event(2, self("a"))); // busy: skipped entirely
		engine.tick(6);
		engine.onEntityEvent(event(6, self("a")));
		assertEquals(List.of(1L, 6L), seen);
	}

	@Test void appliesGlobalCooldownMultiplierAndChangesItImmediately() {
		List<Long> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(event.gameTick()));
		engine.reload(new EefRuleSet("test",2,rules("""
			{"id":"cool","event":"entity_damaged","cooldown":{"scope":"rule_entity","ticks":20},"actions":[{"type":"mark"}]}
			""")));
		engine.setCooldownMultiplier(0.5);
		engine.onEntityEvent(event(1, self("a"))); engine.onEntityEvent(event(10, self("a"))); engine.onEntityEvent(event(11, self("a")));
		assertEquals(List.of(1L, 11L), seen);
		engine.setCooldownMultiplier(2.0); // also releases cooldowns created with the previous setting
		engine.onEntityEvent(event(12, self("a"))); engine.onEntityEvent(event(51, self("a"))); engine.onEntityEvent(event(52, self("a")));
		assertEquals(List.of(1L, 11L, 12L, 52L), seen);
	}

	@Test void schedulesTimedSubtitleCues() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(action.type()+":"+action.parameters().get("text")));
		engine.reload(new EefRuleSet("test",2,rules("""
			{"event":"entity_damaged","actions":[{"type":"play_sound","sound":"x:y"},{"type":"subtitle","text":"Ow!","delay_ticks":3}]}
			""")));
		engine.onEntityEvent(event(20,self("a")));
		assertEquals(List.of("play_sound:null"), seen); assertEquals(1, engine.scheduledActionCount());
		engine.tick(22); assertEquals(1, seen.size()); engine.tick(23);
		assertEquals(List.of("play_sound:null","subtitle:Ow!"), seen);
	}

	@Test void tracksExclusiveSequenceStateAndRunsSteps() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(action.type()));
		engine.reload(new EefRuleSet("test",2,rules("""
			{"id":"talk","event":"entity_damaged","actions":[{"type":"sequence","key":"conversation","mode":"ignore","steps":[
			{"delay_ticks":0,"actions":[{"type":"first"}]},{"delay_ticks":5,"actions":[{"type":"second"}]}]}]}
			""")));
		engine.onEntityEvent(event(10,self("a")));
		assertTrue(engine.isSequenceActive("conversation:a",10));
		engine.onEntityEvent(event(11,self("a"))); // ignored while active
		engine.tick(11); engine.tick(15);
		assertEquals(List.of("first","second"), seen);
	}

	@Test void exposesConversationStateToLaterRulesAndExpiresIt() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(action.type()));
		EefRuleSet set = EefRuleParser.parse("""
			{"schema":2,"rules":[
			{"id":"start","event":"entity_damaged","actions":[{"type":"set_state","key":"talking","value":true,"ttl_ticks":5}]},
			{"id":"reply","event":"entity_hurt","conditions":[{"path":"state.talking","operator":"eq","value":true}],"actions":[{"type":"reply"}]}]}
			""");
		engine.reload(set); engine.onEntityEvent(event(10,self("a"))); engine.onEntityEvent(event(EntityEventKind.ENTITY_HURT,11,self("a")));
		assertEquals(List.of("reply"),seen);
		engine.onEntityEvent(event(EntityEventKind.ENTITY_HURT,12,self("b"))); // entity-scoped
		engine.onEntityEvent(event(EntityEventKind.ENTITY_HURT,16,self("a"))); // expired
		assertEquals(List.of("reply"),seen);
	}

	@Test void zeroChanceNeverSelectsAnOutcome() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(action.type()));
		engine.reload(new EefRuleSet("test",2,rules("{\"event\":\"entity_damaged\",\"chance\":0,\"actions\":[{\"type\":\"never\"}]}")));
		engine.onEntityEvent(event(1,self("a")));
		assertTrue(seen.isEmpty());
	}

	@Test void reloadClearsCooldownsScheduledActionsAndConversationState() {
		List<Long> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(event.gameTick()));
		EefRuleSet set = new EefRuleSet("test",2,rules("""
			{"id":"r","event":"entity_damaged","cooldown":{"ticks":100},"actions":[{"type":"mark"},{"type":"late","delay_ticks":20}]}
			"""));
		engine.reload(set); engine.onEntityEvent(event(1,self("a"))); assertEquals(1,engine.scheduledActionCount());
		engine.reload(set); assertEquals(0,engine.scheduledActionCount()); engine.onEntityEvent(event(2,self("a")));
		assertEquals(List.of(1L,2L),seen);
	}

	@Test void unknownActionIsSafelyDelegatedForExtensionOrIgnore() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(action.type()));
		engine.reload(new EefRuleSet("test",2,rules("{" + "\"event\":\"entity_damaged\",\"actions\":[{\"type\":\"future_extension\"}]}")));
		engine.onEntityEvent(event(1,self("a")));
		assertEquals(List.of("future_extension"),seen);
	}

	@Test void detectsOnlyCompleteGuaranteedSoundReplacementRules() {
		EntityEvent damage = event(1,self("a"));
		EefRuleEngine engine = new EefRuleEngine();
		engine.reload(new EefRuleSet("test",2,rules("""
			{"event":"entity_damaged","conditions":{"self.type":"minecraft:villager"},
			"outcomes":[{"id":"a","actions":[{"type":"play_sound","sound":"example:a"}]},
			{"id":"b","actions":[{"type":"play_sound","sound":"example:b"}]}]}
			""")));
		assertTrue(engine.hasMatchingSoundReplacement(damage));
		engine.reload(new EefRuleSet("test",2,rules("""
			{"event":"entity_damaged","chance":0.5,"actions":[{"type":"play_sound","sound":"example:a"}]}
			""")));
		assertFalse(engine.hasMatchingSoundReplacement(damage));
	}

	@Test void detectsPackProvidedEntitySoundFamiliesForVanillaSuppression() {
		EefRuleEngine engine = new EefRuleEngine();
		engine.reload(new EefRuleSet("test",2,rules("""
			{"event":"entity_observed","conditions":[
			{"path":"self.type","operator":"eq","value":"minecraft:villager"},
			{"path":"self.ai.primary_activity","operator":"eq","value":"idle"}],
			"actions":[{"type":"play_sound","sound":"example:idle"}]}
			""")));
		assertTrue(engine.hasSoundProvider(EntityEventKind.ENTITY_OBSERVED, "minecraft:villager"));
		assertFalse(engine.hasSoundProvider(EntityEventKind.ENTITY_OBSERVED, "minecraft:pig"));
	}

	@Test void collectionIntersectionConditionSupportsNearbyBlockFamilies() {
		EefCondition condition = new EefCondition("blocks", EefCondition.Operator.INTERSECTS,
			List.of("minecraft:campfire", "minecraft:soul_campfire"));
		assertTrue(condition.test(List.of("minecraft:stone", "minecraft:campfire")));
		assertFalse(condition.test(List.of("minecraft:stone", "minecraft:dirt")));
	}

	@Test void notContainsSupportsCollectionsAndStrings() {
		assertTrue(new EefCondition("effects", EefCondition.Operator.NOT_CONTAINS, "minecraft:invisibility")
			.test(List.of("minecraft:speed")));
		assertFalse(new EefCondition("effects", EefCondition.Operator.NOT_CONTAINS, "minecraft:invisibility")
			.test(List.of("minecraft:invisibility")));
		assertTrue(new EefCondition("text", EefCondition.Operator.NOT_CONTAINS, "villager").test("trader"));
	}

	@Test void derivesProximityThresholdsFromResourcePackConditions() {
		EefRuleEngine engine = new EefRuleEngine();
		engine.reload(new EefRuleSet("test",2,rules("""
			{"event":"entity_entered_range","conditions":[
			{"path":"self.type","operator":"eq","value":"minecraft:pig"},
			{"path":"distance.observer_to_self","operator":"lte","value":6}]}
			""")));
		assertEquals(Set.of(6.0), engine.proximityThresholds());
	}

	@Test void bindsNearestMatchingSubjectBeforeConditionsAndActions() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(event.entityKey() + ":" + event.value("actor.id").orElse("")));
		engine.setSubjectResolver((query,event) -> List.of(
			rebound(event,"near-pig","minecraft:pig"), rebound(event,"near-villager","minecraft:villager"),
			rebound(event,"far-villager","minecraft:villager")));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"block_broken","subjects":{"self":{"radius":16,"conditions":{"type":"minecraft:villager"}}},
			"conditions":{"actor.type":"minecraft:player"},"actions":[{"type":"speak"}]}]}
			"""));
		engine.onEntityEvent(event(EntityEventKind.BLOCK_BROKEN, 4, Map.of("self",Map.of("id","player"),"actor",Map.of("id","player","type","minecraft:player"))));
		assertEquals(List.of("near-villager:player"), seen);
	}

	@Test void parsedSubjectConditionsMatchAnUnwrappedCandidateContext() {
		EefRule rule = rules("""
			{"event":"entity_observed","subjects":{"self":{"radius":12,"conditions":[
			{"path":"type","operator":"eq","value":"minecraft:villager"},
			{"path":"custom_name","operator":"not_exists"}]}}}
			""").getFirst();
		EefSubjectQuery query = rule.subjects().getFirst();
		assertEquals(List.of("self.type","self.custom_name"), query.conditions().stream().map(EefCondition::path).toList());
		assertTrue(query.matchesCandidate(Map.of("type","minecraft:villager")));
		assertFalse(query.matchesCandidate(Map.of("type","minecraft:pig")));
		assertFalse(query.matchesCandidate(Map.of("type","minecraft:villager","custom_name","Bob")));
	}

	@Test void reusesIdenticalSubjectResolutionAcrossRulesForOneEvent() {
		java.util.concurrent.atomic.AtomicInteger resolutions = new java.util.concurrent.atomic.AtomicInteger();
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(action.type()));
		engine.setSubjectResolver((query,event) -> {
			resolutions.incrementAndGet();
			return List.of(rebound(event,"speaker","minecraft:villager"));
		});
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[
			{"id":"one","event":"entity_observed","subjects":{"self":{"origin":"self","radius":12,
			"conditions":{"type":"minecraft:villager"}}},"actions":[{"type":"one"}]},
			{"id":"two","event":"entity_observed","subjects":{"self":{"origin":"self","radius":12,
			"conditions":{"type":"minecraft:villager"}}},"actions":[{"type":"two"}]}]}
			"""));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, 1, self("pig")));
		assertEquals(1, resolutions.get());
		assertEquals(List.of("one","two"), seen);
	}

	@Test void rejectsPreservedParticipantMismatchBeforeResolvingSubjects() {
		java.util.concurrent.atomic.AtomicInteger resolutions = new java.util.concurrent.atomic.AtomicInteger();
		EefRuleEngine engine = new EefRuleEngine();
		engine.setSubjectResolver((query,event) -> { resolutions.incrementAndGet(); return List.of(); });
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"entity_observed","subjects":{"self":{"origin":"self","radius":12,
			"preserve_as":"target","conditions":{"type":"minecraft:villager"}}},
			"conditions":{"target.type":"minecraft:pig"},"actions":[{"type":"notice"}]}]}
			"""));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, 1,
			Map.of("self",Map.of("id","item","type","minecraft:item"))));
		assertEquals(0, resolutions.get());
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, 2,
			Map.of("self",Map.of("id","pig","type","minecraft:pig"))));
		assertEquals(1, resolutions.get());
	}

	@Test void indexesCapturesAndNarrowsStateSnapshotsToRuleEntityTypes() {
		EefRuleEngine engine = new EefRuleEngine();
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[
			{"event":"entity_state_changed","conditions":{"self.type":"minecraft:villager"}},
			{"event":"entity_state_changed","conditions":[{"path":"self.type","operator":"in","value":["minecraft:player"]}]}]}
			"""));
		assertTrue(engine.captures(EntityEventKind.ENTITY_STATE_CHANGED));
		assertFalse(engine.captures(EntityEventKind.ENTITY_DAMAGED));
		assertTrue(engine.observesEntityState("minecraft:villager"));
		assertTrue(engine.observesEntityState("minecraft:player"));
		assertFalse(engine.observesEntityState("minecraft:cow"));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"entity_state_changed","conditions":{"change.path":"sleeping"}}]}
			"""));
		assertTrue(engine.observesEntityState("minecraft:cow"));
	}

	@Test void allSubjectSelectionHonorsLimitAndUsesBoundEntityCooldownKeys() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(event.entityKey()));
		engine.setSubjectResolver((query,event) -> List.of(rebound(event,"a","minecraft:pig"), rebound(event,"b","minecraft:pig"), rebound(event,"c","minecraft:pig")));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[{"id":"nearby","event":"block_placed","subjects":{"self":{"selection":"all","limit":2}},
			"cooldown":{"scope":"rule_entity","ticks":10},"actions":[{"type":"mark"}]}]}
			"""));
		EntityEvent first = event(EntityEventKind.BLOCK_PLACED, 1, Map.of("self",Map.of("id","player")));
		engine.onEntityEvent(first); engine.onEntityEvent(event(EntityEventKind.BLOCK_PLACED, 2, first.context()));
		assertEquals(List.of("a","b"), seen);
	}

	@Test void synchronizedFanoutWaitsForEverySpeakerAndStartsTheBatchTogether() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> {
			if (action.type().equals("chorus")) seen.add(event.entityKey());
		});
		engine.setSubjectResolver((query,event) -> List.of(
			rebound(event,"a","minecraft:villager"), rebound(event,"b","minecraft:villager")));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[
			{"id":"busy","event":"entity_loaded","conditions":{"self.id":"a"},"actions":[
			 {"type":"play_sound","sound":"test:busy","channel":"dialogue","channel_scope":"entity","duration_ticks":10}]},
			{"id":"chorus","event":"block_placed","subjects":{"self":{"selection":"all","synchronize":true}},
			 "cooldown":{"scope":"rule","ticks":20},"actions":[
			 {"type":"play_sound","sound":"test:chorus","channel":"dialogue","channel_scope":"entity","duration_ticks":5},
			 {"type":"chorus"}]}
			]}
			"""));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_LOADED, 1, Map.of("self",Map.of("id","a","type","minecraft:villager"))));
		engine.onEntityEvent(event(EntityEventKind.BLOCK_PLACED, 2, Map.of("self",Map.of("id","player"))));
		engine.tick(10);
		assertTrue(seen.isEmpty());
		engine.tick(11);
		assertEquals(List.of("a","b"), seen);
	}

	@Test void subjectRuleDoesNothingWhenNoCandidateExists() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(action.type()));
		engine.setSubjectResolver((query,event) -> List.of());
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"block_broken","subjects":{"self":{"radius":8}},"actions":[{"type":"never"}]}]}
			"""));
		engine.onEntityEvent(event(EntityEventKind.BLOCK_BROKEN, 1, self("player")));
		assertTrue(seen.isEmpty());
	}

	@Test void evaluatesPreservedParticipantAndRelationshipContext() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(
			event.value("self.id").orElse("") + "->" + event.value("target.id").orElse("")));
		engine.setSubjectResolver((query,event) -> List.of(new EntityEvent(event.gameTick(), event.kind(), null, null, null, null,
			event.reliability(), Map.of(
				"self", Map.of("id","witness","type","minecraft:villager"),
				"target", Map.of("id","pig","type","minecraft:pig"),
				"relationships", Map.of("self_to_target", Map.of("distance",5.0,"line_of_sight",true,"same_entity",false))))));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"entity_loaded","subjects":{"self":{"origin":"self","radius":12,
			"preserve_as":"target","exclude":"target","line_of_sight":true,"conditions":{"type":"minecraft:villager"}}},
			"conditions":{"target.type":"minecraft:pig","relationships.self_to_target.line_of_sight":true,
			"relationships.self_to_target.same_entity":false},"actions":[{"type":"notice"}]}]}
			"""));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_LOADED, 1, Map.of("self",Map.of("id","pig","type","minecraft:pig"))));
		assertEquals(List.of("witness->pig"), seen);
	}

	@Test void recursivelyDiffsStateSnapshotsIncludingAddedAndRemovedValues() {
		List<EefStateDiff.Change> changes = EefStateDiff.between(
			Map.of("sleeping",false,"equipment",Map.of("main_hand","minecraft:air"),"old_value",true),
			Map.of("sleeping",true,"equipment",Map.of("main_hand","minecraft:bread"),"new_value",4));
		assertEquals(List.of("equipment.main_hand","new_value","old_value","sleeping"), changes.stream().map(EefStateDiff.Change::path).toList());
		assertFalse(changes.get(1).beforeExists());
		assertFalse(changes.get(2).afterExists());
	}

	@Test void stateChangeRulesCanMatchPathBeforeAfterAndCurrentEntityState() {
		EefRule rule = rules("""
			{"event":"entity_state_changed","conditions":{
			"self.type":"minecraft:villager","self.sleeping":true,"change.path":"sleeping",
			"change.before":false,"change.after":true,"snapshot.before.sleeping":false}}
			""").getFirst();
		EntityEvent changed = event(EntityEventKind.ENTITY_STATE_CHANGED, 8, Map.of(
			"self",Map.of("id","v","type","minecraft:villager","sleeping",true),
			"change",Map.of("path","sleeping","before",false,"after",true),
			"snapshot",Map.of("before",Map.of("sleeping",false),"after",Map.of("sleeping",true))));
		assertTrue(EefRuleEngine.matches(rule, changed));
	}

	@Test void proximityObservationRearmsOnlyAfterExitOrObservationLoss() {
		List<Long> seen = new ArrayList<>();
		java.util.concurrent.atomic.AtomicBoolean nearby = new java.util.concurrent.atomic.AtomicBoolean(true);
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(event.gameTick()));
		engine.setSubjectResolver((query,event) -> nearby.get() ? List.of(rebound(event,"speaker","minecraft:villager")) : List.of());
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[{"id":"notice","event":"entity_observed","subjects":{"self":{
			"origin":"self","radius":12,"retrigger":"after_exit","exit_grace_ticks":40}},
			"cooldown":{"ticks":1},"actions":[{"type":"notice"}]}]}
			"""));
		Map<String,Object> pig = Map.of("self",Map.of("id","pig","type","minecraft:pig"));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED,1,pig));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED,30,pig));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED,70,pig));
		assertEquals(List.of(1L), seen, "cooldown expiry must not retrigger a continuous match");
		nearby.set(false); engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED,71,pig));
		nearby.set(true); engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED,72,pig));
		assertEquals(List.of(1L,72L), seen, "an explicit no-match pulse rearms the rule");
		engine.tick(113); engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED,114,pig));
		assertEquals(List.of(1L,72L,114L), seen, "loss of observation past the grace window also rearms it");
	}

	@Test void worldSessionResetClearsEncounterStateButKeepsLoadedRules() {
		List<Long> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(event.gameTick()));
		engine.setSubjectResolver((query,event) -> List.of(rebound(event,"speaker","minecraft:villager")));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[{"id":"notice","event":"entity_observed","subjects":{"self":{
			"origin":"self","radius":12,"retrigger":"after_exit","exit_grace_ticks":100}},
			"cooldown":{"ticks":1200},"actions":[{"type":"notice"}]}]}
			"""));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, 5000, self("pig")));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, 5020, self("pig")));
		engine.resetRuntimeState();
		assertEquals(1, engine.rules().size());
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, 20, self("pig")));
		assertEquals(List.of(5000L,20L), seen);
	}

	@Test void authoritativeEventSuppressesItsDeferredInferredDuplicate() {
		EefAuthoritativeEventGate gate = new EefAuthoritativeEventGate();
		gate.defer(event(10, self("villager")), 2);
		assertEquals(1, gate.pendingCount());
		gate.authoritative(EntityEventKind.ENTITY_DAMAGED, "villager", 15);
		assertTrue(gate.drain(12).isEmpty());
		assertEquals(0, gate.pendingCount());
	}

	@Test void inferredEventFallsBackWhenNoAuthoritativePacketArrives() {
		EefAuthoritativeEventGate gate = new EefAuthoritativeEventGate();
		EntityEvent inferred = event(20, self("villager"));
		gate.defer(inferred, 2);
		assertTrue(gate.drain(21).isEmpty());
		assertEquals(List.of(inferred), gate.drain(22));
	}

	@Test void enteredActivationFiresOnceUntilPredicateBecomesFalse() {
		List<Long> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(event.gameTick()));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[{"id":"low","event":"entity_observed","activation":{"mode":"entered"},
			"conditions":{"self.low":true},"actions":[{"type":"notice"}]}]}
			"""));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, 1, Map.of("self",Map.of("id","p","low",true))));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, 2, Map.of("self",Map.of("id","p","low",true))));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, 3, Map.of("self",Map.of("id","p","low",false))));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, 4, Map.of("self",Map.of("id","p","low",true))));
		assertEquals(List.of(1L,4L), seen);
	}

	@Test void enteredActivationHonorsDwellAndConfiguredIdentityPath() {
		List<Long> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(event.gameTick()));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[{"id":"still","event":"entity_observed",
			"activation":{"mode":"entered","hold_ticks":20,"key":"target.id"},
			"conditions":{"target.still":true},"actions":[{"type":"notice"}]}]}
			"""));
		for (long tick : List.of(10L,20L,30L,40L)) engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, tick,
			Map.of("self",Map.of("id","speaker"),"target",Map.of("id","player","still",true))));
		assertEquals(List.of(30L), seen);
	}

	@Test void exitedActivationFiresAfterAQualifiedMatchEnds() {
		List<Long> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(event.gameTick()));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[{"id":"wake","event":"entity_observed",
			"activation":{"mode":"exited","hold_ticks":5},"conditions":{"self.sleeping":true},
			"actions":[{"type":"notice"}]}]}
			"""));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, 1, Map.of("self",Map.of("id","p","sleeping",true))));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, 6, Map.of("self",Map.of("id","p","sleeping",true))));
		engine.onEntityEvent(event(EntityEventKind.ENTITY_OBSERVED, 7, Map.of("self",Map.of("id","p","sleeping",false))));
		assertEquals(List.of(7L), seen);
	}

	@Test void derivesNaturalAndSkippedWorldTimeTransitions() {
		Map<String,Object> natural = EefTimeTransition.between(1000, 1020);
		assertEquals(false, natural.get("skipped"));
		Map<String,Object> night = EefTimeTransition.between(6000, 13000);
		assertEquals(true, night.get("skipped"));
		assertEquals(true, night.get("crossed_to_night"));
		Map<String,Object> day = EefTimeTransition.between(18000, 24000);
		assertEquals(true, day.get("crossed_to_day"));
	}

	@Test void exposesDeterministicCalendarPredicates() {
		Map<String,Object> calendar = EefCalendarContext.at(java.time.ZonedDateTime.parse("2026-12-25T14:00:00+02:00"));
		assertEquals("friday", calendar.get("day_of_week"));
		assertEquals(12, calendar.get("month")); assertEquals(25, calendar.get("day"));
		assertEquals(true, calendar.get("top_of_hour")); assertEquals(false, calendar.get("weekend"));
	}

	@Test void serializesWholeSoundAndSubtitleBundlesOnAnAudioChannel() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(action.type()));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[
			{"id":"one","event":"entity_damaged","actions":[
			{"type":"play_sound","sound":"test:one","channel":"voice","channel_scope":"global","duration_ticks":5,"gap_ticks":2},
			{"type":"subtitle","text":"one","delay_ticks":1}]},
			{"id":"two","event":"entity_damaged","actions":[
			{"type":"play_sound","sound":"test:two","channel":"voice","channel_scope":"global","duration_ticks":5,"gap_ticks":2},
			{"type":"subtitle","text":"two","delay_ticks":1}]}]}
			"""));
		engine.onEntityEvent(event(10, self("villager")));
		assertEquals(List.of("play_sound"), seen);
		assertEquals(3, engine.scheduledActionCount());
		engine.tick(11); assertEquals(List.of("play_sound","subtitle"), seen);
		engine.tick(16); assertEquals(2, seen.size());
		engine.tick(17); assertEquals(List.of("play_sound","subtitle","play_sound"), seen);
		engine.tick(18); assertEquals(List.of("play_sound","subtitle","play_sound","subtitle"), seen);
	}

	@Test void entityAudioChannelsAreIndependentButSerializeOneSpeaker() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(action.type() + ':' + event.entityKey()));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[
			{"id":"talk","event":"entity_damaged","actions":[
			{"type":"play_sound","sound":"test:voice","channel":"voice","channel_scope":"entity","duration_ticks":5}]}]}
			"""));
		engine.onEntityEvent(event(10, self("a")));
		engine.onEntityEvent(event(10, self("b")));
		engine.onEntityEvent(event(10, self("a")));
		assertEquals(List.of("play_sound:a", "play_sound:b"), seen);
		engine.tick(15);
		assertEquals(List.of("play_sound:a", "play_sound:b", "play_sound:a"), seen);
	}

	@Test void conversationAudioChannelOrdersDifferentSpeakersInTheSameConversation() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(action.type() + ':' + event.entityKey()));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[
			{"id":"reply","event":"entity_damaged","actions":[
			{"type":"play_sound","sound":"test:voice","channel":"voice","channel_scope":"conversation",
			 "channel_key_path":"conversation.id","duration_ticks":5}]}]}
			"""));
		engine.onEntityEvent(event(10, Map.of("self",Map.of("id","a"),"conversation",Map.of("id","pair"))));
		engine.onEntityEvent(event(10, Map.of("self",Map.of("id","b"),"conversation",Map.of("id","pair"))));
		assertEquals(List.of("play_sound:a"), seen);
		engine.tick(15);
		assertEquals(List.of("play_sound:a", "play_sound:b"), seen);
	}

	@Test void overlapChannelDoesNotWaitForAnExistingSpeakerLine() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(String.valueOf(action.parameters().get("sound"))));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[
			{"id":"normal","event":"entity_damaged","actions":[
			 {"type":"play_sound","sound":"test:normal","channel":"voice","duration_ticks":100}]},
			{"id":"bell","event":"entity_damaged","actions":[
			 {"type":"play_sound","sound":"test:bell","channel":"voice","channel_mode":"overlap","duration_ticks":5}]}]}
			"""));
		engine.onEntityEvent(event(10, self("a")));
		assertEquals(List.of("test:normal", "test:bell"), seen);
	}

	@Test void developerPlaybackResolvesFriendlyNamesAndExplicitVariants() {
		List<String> seen = new ArrayList<>();
		EefRuleEngine engine = new EefRuleEngine((action,event) -> seen.add(action.type()));
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[{"id":"block_abcdef_hear_a_bell_ring","event":"player_interacted_with_block","chance":0,
			"conditions":{"self.type":"minecraft:villager"},"outcomes":[
			{"id":"variant_01","actions":[{"type":"first"}]},
			{"id":"variant_02","actions":[{"type":"second"}]}]}]}
			"""));
		EefRuleEngine.ManualPlayResult result = engine.playManual("hear a bell ring", 2, event(5, self("speaker")));
		assertEquals(EefRuleEngine.ManualPlayStatus.PLAYED, result.status());
		assertEquals("block_abcdef_hear_a_bell_ring", result.ruleId());
		assertEquals("variant_02", result.outcomeId());
		assertEquals(2, result.variant());
		assertEquals(List.of("second"), seen); // explicit playback bypasses chance and predicates
	}

	@Test void developerPlaybackReportsAmbiguityAndVariantBounds() {
		EefRuleEngine engine = new EefRuleEngine();
		engine.reload(EefRuleParser.parse("""
			{"schema":2,"rules":[
			{"id":"one_open_door","event":"player_interacted_with_block","actions":[{"type":"one"}]},
			{"id":"two_open_door","event":"player_interacted_with_block","actions":[{"type":"two"}]}]}
			"""));
		assertEquals(EefRuleEngine.ManualPlayStatus.AMBIGUOUS,
			engine.playManual("open door", 0, event(1, self("a"))).status());
		EefRuleEngine.ManualPlayResult invalid = engine.playManual("one_open_door", 2, event(1, self("a")));
		assertEquals(EefRuleEngine.ManualPlayStatus.INVALID_VARIANT, invalid.status());
		assertEquals(1, invalid.variantCount());
		assertEquals(2, engine.ruleCatalog().size());
	}

	private static List<EefRule> rules(String rule) { return EefRuleParser.parse("{\"schema\":2,\"rules\":["+rule+"]}").rules(); }
	private static Map<String,Object> self(String id) { return Map.of("self",Map.of("id",id,"type","minecraft:villager")); }
	private static EntityEvent event(long tick, Map<String,Object> context) { return new EntityEvent(tick,EntityEventKind.ENTITY_DAMAGED,null,null,null,null,EventReliability.CLIENT_INFERRED,context); }
	private static EntityEvent event(EntityEventKind kind, long tick, Map<String,Object> context) { return new EntityEvent(tick,kind,null,null,null,null,EventReliability.CLIENT_INFERRED,context); }
	private static EntityEvent rebound(EntityEvent event, String id, String type) {
		Map<String,Object> context = new LinkedHashMap<>(event.context()); context.put("self",Map.of("id",id,"type",type));
		return new EntityEvent(event.gameTick(),event.kind(),null,null,event.position(),event.detail(),event.reliability(),context);
	}
}
