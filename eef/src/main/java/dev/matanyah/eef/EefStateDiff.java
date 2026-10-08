package dev.matanyah.eef;

import java.util.*;

/** Deterministic recursive diff for client-observable entity state snapshots. */
public final class EefStateDiff {
	private EefStateDiff() {}

	public record Change(String path, boolean beforeExists, Object before, boolean afterExists, Object after) {}

	public static List<Change> between(Map<String,Object> before, Map<String,Object> after) {
		Map<String,Object> beforeFlat = new TreeMap<>(), afterFlat = new TreeMap<>();
		flatten("", before, beforeFlat); flatten("", after, afterFlat);
		Set<String> paths = new TreeSet<>(beforeFlat.keySet()); paths.addAll(afterFlat.keySet());
		List<Change> changes = new ArrayList<>();
		for (String path : paths) {
			boolean hadBefore = beforeFlat.containsKey(path), hasAfter = afterFlat.containsKey(path);
			Object oldValue = beforeFlat.get(path), newValue = afterFlat.get(path);
			if (hadBefore == hasAfter && Objects.deepEquals(oldValue, newValue)) continue;
			changes.add(new Change(path, hadBefore, oldValue, hasAfter, newValue));
		}
		return List.copyOf(changes);
	}

	private static void flatten(String prefix, Map<?,?> source, Map<String,Object> result) {
		for (Map.Entry<?,?> entry : source.entrySet()) {
			String path = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
			if (entry.getValue() instanceof Map<?,?> nested) flatten(path, nested, result);
			else result.put(path, entry.getValue());
		}
	}
}
