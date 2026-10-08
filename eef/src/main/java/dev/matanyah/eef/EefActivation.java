package dev.matanyah.eef;

/** Controls edge/dwell semantics for conditions evaluated on recurring events. */
public record EefActivation(Mode mode, long holdTicks, long resetAfterTicks, String keyPath) {
	public enum Mode { MATCH, ENTERED, EXITED }

	public static final EefActivation DEFAULT = new EefActivation(Mode.MATCH, 0, 100, null);

	public EefActivation {
		if (mode == null) throw new IllegalArgumentException("Activation mode is missing");
		if (holdTicks < 0) throw new IllegalArgumentException("Activation hold_ticks cannot be negative");
		if (resetAfterTicks < 1) throw new IllegalArgumentException("Activation reset_after_ticks must be positive");
		if (mode == Mode.MATCH && holdTicks != 0) throw new IllegalArgumentException("Activation hold_ticks requires entered or exited mode");
		if (keyPath != null && keyPath.isBlank()) keyPath = null;
	}
}
