package dev.matanyah.eef;

/** Executes an action emitted by a matching EEF rule. Implementations may be client or server side. */
@FunctionalInterface
public interface EefActionHandler {
	void execute(EefAction action, EntityEvent event);
}
