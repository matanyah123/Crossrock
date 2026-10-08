package dev.matanyah.eef;

import java.util.Map;

/** A data-driven action requested by an EEF rule. Implementations may be supplied by integrations. */
public record EefAction(String type, Map<String, Object> parameters) {
	public EefAction {
		if (type == null || type.isBlank()) throw new IllegalArgumentException("Action type is blank");
		parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
	}
}
