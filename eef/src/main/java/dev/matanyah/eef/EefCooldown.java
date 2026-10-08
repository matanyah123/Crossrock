package dev.matanyah.eef;

/** A tick-based cooldown. RULE_ENTITY is the normal dialogue anti-spam scope. */
public record EefCooldown(long ticks, Scope scope) {
	public enum Scope { GLOBAL, RULE, ENTITY, RULE_ENTITY }
	public EefCooldown {
		if (ticks < 0) throw new IllegalArgumentException("Cooldown ticks cannot be negative");
		if (scope == null) scope = Scope.RULE_ENTITY;
	}
}
