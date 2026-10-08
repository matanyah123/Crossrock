package dev.matanyah.eef;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public final class EntityEventBus {
	public static final Event<EntityEventListener> EVENT = EventFactory.createArrayBacked(
		EntityEventListener.class, listeners -> event -> {
			for (EntityEventListener listener : listeners) listener.onEntityEvent(event);
		});

	private EntityEventBus() {
	}

	public static void emit(EntityEvent event) {
		EVENT.invoker().onEntityEvent(event);
	}
}
