package dev.matanyah.eef;

import java.util.*;

/** Holds inferred events briefly so authoritative equivalents can replace them. */
public final class EefAuthoritativeEventGate {
	private record Pending(EntityEvent event, long dueTick) {}
	private final Map<String,Pending> pending = new LinkedHashMap<>();
	private final Map<String,Long> authoritativeUntil = new HashMap<>();

	public synchronized void defer(EntityEvent event, long delayTicks) {
		String key = key(event.kind(), event.entityKey());
		if (authoritativeUntil.getOrDefault(key, Long.MIN_VALUE) >= event.gameTick()) return;
		pending.put(key, new Pending(event, event.gameTick() + delayTicks));
	}

	public synchronized void authoritative(EntityEventKind kind, String entityKey, long untilTick) {
		String key = key(kind, entityKey);
		authoritativeUntil.put(key, untilTick);
		pending.remove(key);
	}

	public synchronized List<EntityEvent> drain(long gameTick) {
		List<EntityEvent> result = new ArrayList<>();
		Iterator<Map.Entry<String,Pending>> iterator = pending.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String,Pending> entry = iterator.next();
			if (entry.getValue().dueTick() <= gameTick) {
				if (authoritativeUntil.getOrDefault(entry.getKey(), Long.MIN_VALUE) < gameTick) result.add(entry.getValue().event());
				iterator.remove();
			}
		}
		authoritativeUntil.entrySet().removeIf(entry -> entry.getValue() < gameTick);
		return List.copyOf(result);
	}

	public synchronized int pendingCount() { return pending.size(); }
	public synchronized void clear() { pending.clear(); authoritativeUntil.clear(); }
	private static String key(EntityEventKind kind, String entityKey) { return kind.name() + ':' + entityKey; }
}
