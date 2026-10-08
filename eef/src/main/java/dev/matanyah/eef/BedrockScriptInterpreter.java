package dev.matanyah.eef;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Bedrock script compatibility boundary. It extracts Bedrock event subscriptions
 * and dispatches them through EEF; JavaScript execution engines can be plugged in
 * later without changing pack loading or event semantics.
 */
public final class BedrockScriptInterpreter {
	private static final Pattern EVENT = Pattern.compile("afterEvents\\.([A-Za-z0-9_]+)");
	private final Set<String> subscriptions = new LinkedHashSet<>();
	private final EntityEventBusAdapter adapter;

	@FunctionalInterface public interface EntityEventBusAdapter { void dispatch(String bedrockEvent, Map<String, Object> payload); }
	public BedrockScriptInterpreter(EntityEventBusAdapter adapter) { this.adapter = Objects.requireNonNull(adapter); }

	public void load(String javascript) {
		subscriptions.clear();
		var matcher = EVENT.matcher(javascript == null ? "" : javascript);
		while (matcher.find()) subscriptions.add(matcher.group(1));
	}
	public Set<String> subscriptions() { return Set.copyOf(subscriptions); }
	public void dispatch(String event, Map<String,Object> payload) {
		if (subscriptions.contains(event)) adapter.dispatch(event, Map.copyOf(payload == null ? Map.of() : payload));
	}
}
