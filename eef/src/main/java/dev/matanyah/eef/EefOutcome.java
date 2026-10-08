package dev.matanyah.eef;

import java.util.List;

/** A weighted action bundle selected after a rule matches. */
public record EefOutcome(String id, double weight, List<EefAction> actions) {
	public EefOutcome {
		if (id == null || id.isBlank()) throw new IllegalArgumentException("Outcome id is blank");
		if (!Double.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Outcome weight must be positive");
		actions = actions == null ? List.of() : List.copyOf(actions);
	}
}
