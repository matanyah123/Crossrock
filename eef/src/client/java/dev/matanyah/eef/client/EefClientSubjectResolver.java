package dev.matanyah.eef.client;

import dev.matanyah.eef.EefSubjectQuery;
import dev.matanyah.eef.EefSubjectResolver;
import dev.matanyah.eef.EntityEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;

/** Resolves generic entity queries against the currently observed client world. */
public final class EefClientSubjectResolver implements EefSubjectResolver {
	private long contextCacheTick = Long.MIN_VALUE;
	private final IdentityHashMap<Entity, Map<String,Object>> contextCache = new IdentityHashMap<>();

	@Override public List<EntityEvent> resolve(EefSubjectQuery query, EntityEvent event) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return List.of();
		double[] origin = origin(query.origin(), event);
		if (origin == null) return List.of();
		Entity observed = roleEntity(query.origin(), event, client);
		double radiusSquared = query.radius() * query.radius();
		List<Candidate> candidates = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (excluded(query, event, entity)) continue;
			double dx = entity.getX() - origin[0], dy = entity.getY() - origin[1], dz = entity.getZ() - origin[2];
			double distanceSquared = dx * dx + dy * dy + dz * dz;
			if (distanceSquared > radiusSquared) continue;
			Map<String,Object> entityContext = context(entity, event.gameTick());
			if (!query.matchesCandidate(entityContext)) continue;
			if (query.lineOfSight() && (observed == null || !(entity instanceof LivingEntity living) || !living.hasLineOfSight(observed))) continue;
			candidates.add(new Candidate(distanceSquared, rebind(query, event, entity, entityContext, Math.sqrt(distanceSquared))));
		}
		candidates.sort(Comparator.comparingDouble(Candidate::distanceSquared));
		return candidates.stream().map(Candidate::event).toList();
	}

	private Map<String,Object> context(Entity entity, long tick) {
		if (contextCacheTick != tick) { contextCacheTick = tick; contextCache.clear(); }
		return contextCache.computeIfAbsent(entity, EefClientContext::entity);
	}

	private static EntityEvent rebind(EefSubjectQuery query, EntityEvent event, Entity entity,
		Map<String,Object> entityContext, double distance) {
		String binding = query.binding();
		Map<String,Object> context = new LinkedHashMap<>(event.context());
		Object previous = context.get(binding);
		Entity previousEntity = binding.equals("self") ? event.source() : event.target();
		String preserveAs = query.preserveAs();
		if (preserveAs != null && previous != null) context.put(preserveAs, previous);
		else if (binding.equals("self") && !context.containsKey("actor") && previous instanceof Map<?,?> oldSelf) context.put("actor", oldSelf);
		context.put(binding, entityContext);
		Map<String,Object> queryContext = new LinkedHashMap<>();
		if (context.get("query") instanceof Map<?,?> oldQuery)
			for (Map.Entry<?,?> entry : oldQuery.entrySet()) queryContext.put(String.valueOf(entry.getKey()), entry.getValue());
		queryContext.put("distance", distance);
		context.put("query", Collections.unmodifiableMap(queryContext));
		Entity source = binding.equals("self") ? entity : event.source();
		Entity target = binding.equals("target") ? entity : event.target();
		if (binding.equals("self") && preserveAs != null && preserveAs.equals("target")) target = previousEntity;
		context.put("relationships", relationships(context, source, target));
		return new EntityEvent(event.gameTick(), event.kind(), source, target, event.position(), event.detail(), event.reliability(), context);
	}

	private static boolean excluded(EefSubjectQuery query, EntityEvent event, Entity candidate) {
		for (String role : query.exclude()) {
			Entity direct = switch (role) {
				case "self" -> event.source();
				case "target" -> event.target();
				default -> null;
			};
			if (role.equals(query.preserveAs())) direct = query.binding().equals("self") ? event.source() : event.target();
			if (direct != null && direct.getUUID().equals(candidate.getUUID())) return true;
			String contextRole = role.equals(query.preserveAs()) ? query.binding() : role;
			Object id = event.value(contextRole + ".id").orElse(null);
			if (id != null && candidate.getUUID().toString().equals(String.valueOf(id))) return true;
		}
		return false;
	}

	private static Entity roleEntity(String role, EntityEvent event, Minecraft client) {
		if (role.equals("self")) return event.source();
		if (role.equals("target")) return event.target();
		Object id = event.value(role + ".id").orElse(null);
		if (id == null || client.level == null) return null;
		try {
			UUID uuid = UUID.fromString(String.valueOf(id));
			for (Entity entity : client.level.entitiesForRendering()) if (entity.getUUID().equals(uuid)) return entity;
		} catch (IllegalArgumentException ignored) {}
		return null;
	}

	private static Map<String,Object> relationships(Map<String,Object> context, Entity source, Entity target) {
		Map<String,Object> result = new LinkedHashMap<>();
		for (String from : List.of("self", "target", "actor", "subject", "observer"))
			for (String to : List.of("self", "target", "actor", "subject", "observer")) {
				if (from.equals(to)) continue;
				Map<String,Object> relation = relation(context.get(from), context.get(to), entityForRole(from, source, target), entityForRole(to, source, target));
				if (!relation.isEmpty()) result.put(from + "_to_" + to, relation);
			}
		return Collections.unmodifiableMap(result);
	}

	private static Entity entityForRole(String role, Entity source, Entity target) {
		return role.equals("self") ? source : role.equals("target") ? target : null;
	}

	private static Map<String,Object> relation(Object rawFrom, Object rawTo, Entity fromEntity, Entity toEntity) {
		if (!(rawFrom instanceof Map<?,?> from) || !(rawTo instanceof Map<?,?> to)) return Map.of();
		Object fromId = from.get("id"), toId = to.get("id");
		Map<String,Object> result = new LinkedHashMap<>();
		if (fromId != null && toId != null) {
			String fromIdentifier = String.valueOf(fromId), toIdentifier = String.valueOf(toId);
			result.put("same_entity", fromIdentifier.equals(toIdentifier));
			result.put("from_owns_to", fromIdentifier.equals(String.valueOf(to.get("owner_id"))));
			result.put("to_owns_from", toIdentifier.equals(String.valueOf(from.get("owner_id"))));
			result.put("from_targets_to", toIdentifier.equals(nestedId(from.get("attack_target"))));
			result.put("to_targets_from", fromIdentifier.equals(nestedId(to.get("attack_target"))));
			result.put("from_riding_to", toIdentifier.equals(nestedId(from.get("vehicle"))));
			result.put("to_riding_from", fromIdentifier.equals(nestedId(to.get("vehicle"))));
			String fromVehicle = nestedId(from.get("vehicle")), toVehicle = nestedId(to.get("vehicle"));
			result.put("same_vehicle", fromVehicle != null && fromVehicle.equals(toVehicle));
		}
		if (from.get("x") instanceof Number fx && from.get("y") instanceof Number fy && from.get("z") instanceof Number fz
			&& to.get("x") instanceof Number tx && to.get("y") instanceof Number ty && to.get("z") instanceof Number tz) {
			double dx = fx.doubleValue() - tx.doubleValue(), dy = fy.doubleValue() - ty.doubleValue(), dz = fz.doubleValue() - tz.doubleValue();
			result.put("distance", Math.sqrt(dx * dx + dy * dy + dz * dz));
		}
		if (fromEntity instanceof LivingEntity living && toEntity != null) result.put("line_of_sight", living.hasLineOfSight(toEntity));
		return Collections.unmodifiableMap(result);
	}

	private static String nestedId(Object value) {
		return value instanceof Map<?,?> map && map.get("id") != null ? String.valueOf(map.get("id")) : null;
	}

	private static double[] origin(String origin, EntityEvent event) {
		if (origin.equals("event.position") && event.position() != null)
			return new double[]{event.position().getX() + 0.5, event.position().getY() + 0.5, event.position().getZ() + 0.5};
		Object raw = event.context().get(origin);
		if (!(raw instanceof Map<?,?> map) || !(map.get("x") instanceof Number x)
			|| !(map.get("y") instanceof Number y) || !(map.get("z") instanceof Number z)) return null;
		return new double[]{x.doubleValue(), y.doubleValue(), z.doubleValue()};
	}

	private record Candidate(double distanceSquared, EntityEvent event) {}
}
