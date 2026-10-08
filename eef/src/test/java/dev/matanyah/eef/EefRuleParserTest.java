package dev.matanyah.eef;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EefRuleParserTest {
	@Test void parsesRulesAndActions() {
		EefRuleSet set = EefRuleParser.parse("""
			{"id":"example_pack","schema":1,"rules":[
			{"id":"hello","event":"script_event_received","detail":"example:greeting",
			"conditions":{"phase":"day"},"actions":[{"type":"play_sound","sound":"example:greeting"},{"type":"actionbar","text":"hi"}]}]}
			""");
		assertEquals("example_pack", set.id());
		assertEquals(EntityEventKind.SCRIPT_EVENT_RECEIVED, set.rules().getFirst().event());
		assertEquals("example:greeting", set.rules().getFirst().actions().getFirst().parameters().get("sound"));
	}

	@Test void rejectsUnknownEvents() {
		assertThrows(RuntimeException.class, () -> EefRuleParser.parse("{\"rules\":[{\"event\":\"not_real\"}]}"));
	}

	@Test void parsesSchemaTwoConditionsSelectionCooldownsAndReliability() {
		EefRule rule = EefRuleParser.parse("""
			{"schema":2,"rules":[{"id":"damage","event":"entity_damaged",
			"reliability":["CLIENT_INFERRED","SERVER_REQUIRED"],"chance":0.5,
			"conditions":[{"path":"self.type","operator":"eq","value":"minecraft:villager"}],
			"cooldown":{"scope":"rule_entity","ticks":20},"avoid_immediate_repeat":true,
			"outcomes":[{"id":"a","weight":2,"actions":[{"type":"subtitle","text":"ow","delay_ticks":2}]}]}]}
			""").rules().getFirst();
		assertEquals(0.5, rule.chance());
		assertEquals(20, rule.cooldowns().getFirst().ticks());
		assertEquals(2, rule.outcomes().getFirst().weight());
		assertTrue(rule.reliability().contains(EventReliability.SERVER_REQUIRED));
	}

	@Test void parsesAndValidatesActivationTransitions() {
		EefRule rule = EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"entity_observed",
			"activation":{"mode":"entered","hold_ticks":40,"reset_after_ticks":120,"key":"target.id"}}]}
			""").rules().getFirst();
		assertEquals(EefActivation.Mode.ENTERED, rule.activation().mode());
		assertEquals(40, rule.activation().holdTicks());
		assertEquals(120, rule.activation().resetAfterTicks());
		assertEquals("target.id", rule.activation().keyPath());
		assertThrows(RuntimeException.class, () -> EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"entity_observed","activation":{"mode":"forever"}}]}
			"""));
		assertThrows(RuntimeException.class, () -> EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"entity_observed","activation":{"mode":"match","hold_ticks":1}}]}
			"""));
	}

	@Test void rejectsMalformedAndUnknownConditionsButPreservesUnknownActions() {
		assertThrows(RuntimeException.class, () -> EefRuleParser.parse("not json"));
		assertThrows(RuntimeException.class, () -> EefRuleParser.parse("{\"schema\":2}"));
		assertThrows(RuntimeException.class, () -> EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"entity_damaged","conditions":[{"path":"x","operator":"invented"}]}]}
			"""));
		EefRule rule = EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"entity_damaged","actions":[{"type":"extension_action","value":1}]}]}
			""").rules().getFirst();
		assertEquals("extension_action", rule.actions().getFirst().type());
	}

	@Test void parsesDeclarativeNearbySubjectQueries() {
		EefRule rule = EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"block_broken","subjects":{"self":{
			"query":"nearby_entities","origin":"event.position","radius":12,"selection":"nearest","limit":1,
			"preserve_as":"target","exclude":["self","target"],"line_of_sight":true,
			"retrigger":"after_exit","exit_grace_ticks":60,
			"conditions":[{"path":"type","operator":"eq","value":"minecraft:villager"}]}},"actions":[]}]}
			""").rules().getFirst();
		assertEquals(1, rule.subjects().size());
		EefSubjectQuery query = rule.subjects().getFirst();
		assertEquals("self", query.binding());
		assertEquals(12, query.radius());
		assertEquals("self.type", query.conditions().getFirst().path());
		assertEquals("target", query.preserveAs());
		assertEquals(java.util.List.of("self", "target"), query.exclude());
		assertTrue(query.lineOfSight());
		assertEquals(EefSubjectQuery.Retrigger.AFTER_EXIT, query.retrigger());
		assertEquals(60, query.exitGraceTicks());
	}

	@Test void allSubjectQueriesDefaultToBoundedFanoutAndCanSynchronize() {
		EefRule rule = EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"block_broken","subjects":{"self":{
			"selection":"all","synchronize":true}},"actions":[{"type":"mark"}]}]}
			""").rules().getFirst();
		assertEquals(EefSubjectQuery.Selection.ALL, rule.subjects().getFirst().selection());
		assertEquals(256, rule.subjects().getFirst().limit());
		assertTrue(rule.subjects().getFirst().synchronize());
	}

	@Test void rejectsMalformedSubjectQueries() {
		assertThrows(RuntimeException.class, () -> EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"block_broken","subjects":{"speaker":{"query":"nearby_entities"}}}]}
			"""));
		assertThrows(RuntimeException.class, () -> EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"block_broken","subjects":{"self":{"query":"unknown"}}}]}
			"""));
		assertThrows(RuntimeException.class, () -> EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"entity_loaded","subjects":{"self":{"exclude":["mystery"]}}}]}
			"""));
		assertThrows(RuntimeException.class, () -> EefRuleParser.parse("""
			{"schema":2,"rules":[{"event":"entity_observed","subjects":{"self":{"retrigger":"forever"}}}]}
			"""));
	}
}
