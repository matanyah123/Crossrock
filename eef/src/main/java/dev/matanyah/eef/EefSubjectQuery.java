package dev.matanyah.eef;

import java.util.List;
import java.util.Map;

/** A resource-pack-defined query that binds an event role before rule evaluation. */
public record EefSubjectQuery(
	String binding,
	String query,
	String origin,
	double radius,
	List<EefCondition> conditions,
	Selection selection,
	int limit,
	String preserveAs,
	List<String> exclude,
	boolean lineOfSight,
	Retrigger retrigger,
	long exitGraceTicks,
	boolean synchronize
) {
	public enum Selection { NEAREST, RANDOM, ALL }
	public enum Retrigger { COOLDOWN, AFTER_EXIT }

	public EefSubjectQuery {
		if (!binding.equals("self") && !binding.equals("target"))
			throw new IllegalArgumentException("Subject binding must be self or target: " + binding);
		if (!query.equals("nearby_entities")) throw new IllegalArgumentException("Unknown subject query: " + query);
		if (!List.of("event.position", "self", "target", "actor", "observer").contains(origin))
			throw new IllegalArgumentException("Unknown subject query origin: " + origin);
		if (!Double.isFinite(radius) || radius <= 0) throw new IllegalArgumentException("Subject query radius must be positive");
		conditions = conditions == null ? List.of() : List.copyOf(conditions);
		if (selection == null) selection = Selection.NEAREST;
		if (limit < 1) throw new IllegalArgumentException("Subject query limit must be positive");
		if (preserveAs != null && !List.of("target", "actor", "subject").contains(preserveAs))
			throw new IllegalArgumentException("Subject preserve_as must be target, actor, or subject: " + preserveAs);
		exclude = exclude == null ? List.of() : List.copyOf(exclude);
		for (String role : exclude) if (!List.of("self", "target", "actor", "subject", "observer").contains(role))
			throw new IllegalArgumentException("Unknown excluded subject role: " + role);
		if (retrigger == null) retrigger = Retrigger.COOLDOWN;
		if (exitGraceTicks < 0) throw new IllegalArgumentException("Subject exit_grace_ticks cannot be negative");
	}

	/** Evaluates parser-normalized binding paths against one unwrapped candidate context. */
	public boolean matchesCandidate(Map<String,Object> candidate) {
		String prefix = binding + ".";
		for (EefCondition condition : conditions) {
			String path = condition.path().startsWith(prefix) ? condition.path().substring(prefix.length()) : condition.path();
			Object current = candidate;
			for (String part : path.split("\\.")) {
				if (!(current instanceof Map<?,?> map)) { current = null; break; }
				current = map.get(part);
			}
			if (!condition.test(current)) return false;
		}
		return true;
	}
}
