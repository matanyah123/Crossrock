package dev.matanyah.eef;

/** Client/user preferences applied to a loaded rule without changing the pack. */
public interface EefRulePreferences {
	EefRulePreferences DEFAULT = new EefRulePreferences() {};
	default boolean enabled(EefRule rule) { return true; }
	default int frequency(EefRule rule) { return rule.defaultFrequency(); }
	default boolean important(EefRule rule) { return rule.defaultImportant(); }
	default boolean muted() { return false; }
	/** Add-on-style variant weighting: default preserves pack weights, never drops rare variants, often reverses rarity. */
	default double outcomeWeight(EefRule rule, EefOutcome outcome, double maximumWeight) { return outcome.weight(); }
}
