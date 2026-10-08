package dev.matanyah.eef;

import java.util.*;

/** Tick scheduler with optional cancellation keys. */
public final class EefScheduler {
	public record Scheduled(long dueTick, long order, EefAction action, EntityEvent event, String cancellationKey, long generation) {}
	private final PriorityQueue<Scheduled> queue = new PriorityQueue<>(Comparator.comparingLong(Scheduled::dueTick).thenComparingLong(Scheduled::order));
	private final Map<String, Long> generations = new HashMap<>();
	private long order;
	public void schedule(long dueTick, EefAction action, EntityEvent event, String cancellationKey) { queue.add(new Scheduled(dueTick, order++, action, event, cancellationKey, cancellationKey == null ? 0 : generations.getOrDefault(cancellationKey, 0L))); }
	public List<Scheduled> drain(long tick) {
		List<Scheduled> due = new ArrayList<>();
		while (!queue.isEmpty() && queue.peek().dueTick() <= tick) { Scheduled next = queue.remove(); if (next.cancellationKey() == null || next.generation() == generations.getOrDefault(next.cancellationKey(), 0L)) due.add(next); }
		return due;
	}
	public void cancel(String key) { if (key != null) generations.merge(key, 1L, Long::sum); }
	public int size() { return queue.size(); }
	public void clear() { queue.clear(); generations.clear(); order = 0; }
}
