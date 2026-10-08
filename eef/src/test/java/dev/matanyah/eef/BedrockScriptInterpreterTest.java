package dev.matanyah.eef;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BedrockScriptInterpreterTest {
	@Test void extractsAndDispatchesSubscriptions() {
		List<String> seen = new ArrayList<>();
		BedrockScriptInterpreter interpreter = new BedrockScriptInterpreter((event, payload) -> seen.add(event));
		interpreter.load("world.afterEvents.entitySpawn.subscribe(fn); world.afterEvents.scriptEventReceive.subscribe(fn);");
		assertEquals(Set.of("entitySpawn", "scriptEventReceive"), interpreter.subscriptions());
		interpreter.dispatch("entitySpawn", Map.of("id", "x"));
		interpreter.dispatch("entityDie", Map.of());
		assertEquals(List.of("entitySpawn"), seen);
	}
}
