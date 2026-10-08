package dev.matanyah.eef.client;

import dev.matanyah.eef.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.*;

/** Client cache for generic authoritative activity snapshots, with a local fallback. */
final class EefActivityClientState {
	private record Snapshot(String primary, List<String> activities, List<String> behaviors, boolean navigating, boolean stuck,
		Map<String,Integer> inventory, int observerReputation, Map<String,String> memoryDimensions,
		Map<String,Double> memoryDistances) {
		Map<String,Object> context() {
			Map<String,Object> result = new LinkedHashMap<>();
			result.put("primary_activity", primary); result.put("activities", activities); result.put("behaviors", behaviors);
			result.put("navigating", navigating); result.put("stuck", stuck); result.put("observer_reputation", observerReputation);
			Map<String,Object> memories = new LinkedHashMap<>();
			for (String name : List.of("home", "job_site", "meeting_point")) {
				Map<String,Object> memory = new LinkedHashMap<>();
				memory.put("present", memoryDimensions.containsKey(name));
				if (memoryDimensions.containsKey(name)) memory.put("dimension", memoryDimensions.get(name));
				if (memoryDistances.containsKey(name)) memory.put("distance", memoryDistances.get(name));
				memories.put(name, Collections.unmodifiableMap(memory));
			}
			result.put("memories", Collections.unmodifiableMap(memories));
			return Collections.unmodifiableMap(result);
		}
		boolean sameActivity(Snapshot other) { return primary.equals(other.primary) && activities.equals(other.activities)
			&& behaviors.equals(other.behaviors) && navigating == other.navigating && stuck == other.stuck; }
	}
	private static final Map<UUID,Snapshot> SNAPSHOTS = new HashMap<>();
	private EefActivityClientState() {}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(EefActivityPayload.TYPE, (payload, context) ->
			context.client().execute(() -> receive(payload, context.client())));
	}
	static synchronized Map<String,Object> context(Entity entity) {
		Snapshot snapshot = SNAPSHOTS.get(entity.getUUID());
		return (snapshot == null ? local(entity) : snapshot).context();
	}
	static synchronized void clear() { SNAPSHOTS.clear(); }

	private static synchronized void receive(EefActivityPayload payload, Minecraft client) {
		if (client.level == null) return;
		Entity entity = client.level.getEntity(payload.entityId());
		if (!(entity instanceof LivingEntity)) return;
		Snapshot current = new Snapshot(payload.primaryActivity(), payload.activities(), payload.behaviors(),
			payload.navigating(), payload.stuck(), payload.inventory(), payload.observerReputation(),
			payload.memoryDimensions(), payload.memoryDistances());
		Snapshot previous = SNAPSHOTS.put(entity.getUUID(), current);
		if (previous == null) return;
		if (!previous.sameActivity(current)) {
			Map<String,Object> eventContext = baseContext(client, entity);
			eventContext.put("activity", Map.of("before", previous.context(), "after", current.context()));
			EntityEventBus.emit(new EntityEvent(client.level.getGameTime(), EntityEventKind.ENTITY_ACTIVITY_CHANGED,
				entity, null, entity.blockPosition(), null, EventReliability.SERVER_REQUIRED, eventContext));
		}
		if (!previous.inventory().equals(current.inventory())) {
			Map<String,Integer> added = delta(previous.inventory(), current.inventory());
			Map<String,Integer> removed = delta(current.inventory(), previous.inventory());
			Map<String,Object> eventContext = baseContext(client, entity);
			eventContext.put("change", Map.of("path", "inventory", "before", previous.inventory(), "after", current.inventory(),
				"before_exists", true, "after_exists", true));
			eventContext.put("inventory_change", Map.of("added", added, "added_types", List.copyOf(added.keySet()),
				"removed", removed, "removed_types", List.copyOf(removed.keySet())));
			EntityEventBus.emit(new EntityEvent(client.level.getGameTime(), EntityEventKind.ENTITY_STATE_CHANGED,
				entity, null, entity.blockPosition(), null, EventReliability.SERVER_REQUIRED, eventContext));
		}
	}
	static Map<String,Integer> delta(Map<String,Integer> before, Map<String,Integer> after) {
		Map<String,Integer> result = new LinkedHashMap<>();
		after.forEach((type, count) -> { int difference = count - before.getOrDefault(type, 0); if (difference > 0) result.put(type, difference); });
		return Collections.unmodifiableMap(result);
	}
	private static Map<String,Object> baseContext(Minecraft client, Entity entity) {
		Map<String,Object> result = new LinkedHashMap<>(); result.put("self", EefClientContext.entity(entity));
		if (client.player != null) { result.put("observer", EefClientContext.entity(client.player));
			result.put("distance", Map.of("observer_to_self", client.player.distanceTo(entity))); }
		return result;
	}
	private static Snapshot local(Entity entity) {
		if (!(entity instanceof LivingEntity living)) return new Snapshot("none", List.of(), List.of(), false, false,
			Map.of(), 0, Map.of(), Map.of());
		var brain = living.getBrain();
		List<String> activities = brain.getActiveActivities().stream().map(activity -> activity.getName()).sorted().toList();
		String primary = brain.getActiveNonCoreActivity().map(activity -> activity.getName()).orElse("none");
		List<String> behaviors = brain.getRunningBehaviors().stream().map(behavior -> {
			String simple = behavior.getClass().getSimpleName(); return simple.isBlank() ? behavior.debugString() : simple;
		}).distinct().sorted().toList();
		return new Snapshot(primary, activities, behaviors,
			entity instanceof Mob mob && mob.getNavigation().isInProgress(),
			entity instanceof Mob mob && mob.getNavigation().isStuck(), Map.of(), 0, Map.of(), Map.of());
	}
}
