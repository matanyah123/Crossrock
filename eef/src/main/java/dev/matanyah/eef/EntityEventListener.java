package dev.matanyah.eef;

@FunctionalInterface
public interface EntityEventListener {
	void onEntityEvent(EntityEvent event);
}
