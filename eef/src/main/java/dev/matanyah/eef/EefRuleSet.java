package dev.matanyah.eef;

import java.util.List;

public record EefRuleSet(String id, int schema, List<EefRule> rules) {
	public EefRuleSet {
		if (id == null || id.isBlank()) throw new IllegalArgumentException("Rule-set id is blank");
		if (schema < 1) throw new IllegalArgumentException("Unsupported EEF schema");
		rules = rules == null ? List.of() : List.copyOf(rules);
	}
}
