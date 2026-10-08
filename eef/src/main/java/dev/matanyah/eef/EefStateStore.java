package dev.matanyah.eef;

import java.util.*;

/** Expiring scoped values used by conversations and multi-event sequences. */
public final class EefStateStore {
	private record Entry(Object value, long expiresAt) {}
	private final Map<String, Entry> values = new HashMap<>();
	public void put(String scope, String key, Object value, long now, long ttlTicks) { values.put(scope + ':' + key, new Entry(value, ttlTicks <= 0 ? Long.MAX_VALUE : now + ttlTicks)); }
	public Optional<Object> get(String scope, String key, long now) {
		String compound = scope + ':' + key; Entry entry = values.get(compound);
		if (entry == null) return Optional.empty();
		if (entry.expiresAt() < now) { values.remove(compound); return Optional.empty(); }
		return Optional.ofNullable(entry.value());
	}
	public void remove(String scope, String key) { values.remove(scope + ':' + key); }
	public void clear() { values.clear(); }
}
