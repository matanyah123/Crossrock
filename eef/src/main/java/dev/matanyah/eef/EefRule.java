package dev.matanyah.eef;

import java.util.List;
import java.util.Set;

/** Declarative trigger definition loaded from an EEF-enabled resource pack. */
public record EefRule(
	String id,
	EntityEventKind event,
	String detail,
	Set<EventReliability> reliability,
	List<EefSubjectQuery> subjects,
	List<EefCondition> conditions,
	EefActivation activation,
	double chance,
	int defaultFrequency,
	boolean defaultImportant,
	List<EefCooldown> cooldowns,
	boolean avoidImmediateRepeat,
	List<EefOutcome> outcomes
) {
	public EefRule {
		if (id == null || id.isBlank()) throw new IllegalArgumentException("Rule id is blank");
		if (event == null) throw new IllegalArgumentException("Rule event is missing");
		reliability = reliability == null ? Set.of() : Set.copyOf(reliability);
		subjects = subjects == null ? List.of() : List.copyOf(subjects);
		conditions = conditions == null ? List.of() : List.copyOf(conditions);
		activation = activation == null ? EefActivation.DEFAULT : activation;
		if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Rule chance must be in [0,1]");
		if (defaultFrequency < 1 || defaultFrequency > 10) throw new IllegalArgumentException("Rule frequency must be between 1 and 10");
		cooldowns = cooldowns == null ? List.of() : List.copyOf(cooldowns);
		outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
	}

	public List<EefAction> actions() { return outcomes.isEmpty() ? List.of() : outcomes.getFirst().actions(); }
}
