package dev.matanyah.eef.client;

import dev.matanyah.eef.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves authoritative lifecycle entity ids after their client spawn packets arrive. */
final class EefLifecycleClientBridge {
	private record Pending(EefLifecyclePayload payload, long expiresAt) {}
	private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
	private EefLifecycleClientBridge() {}
	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(EefLifecyclePayload.TYPE, (payload, context) ->
			context.client().execute(() -> receive(payload, context.client())));
		ClientTickEvents.END_CLIENT_TICK.register(EefLifecycleClientBridge::tick);
	}
	static void clear() { PENDING.clear(); }
	private static void receive(EefLifecyclePayload payload, Minecraft client) {
		if (!emit(payload, client)) PENDING.addLast(new Pending(payload, payload.gameTick() + 100));
	}
	private static void tick(Minecraft client) {
		if (client.level == null) { PENDING.clear(); return; }
		long now = client.level.getGameTime();
		PENDING.removeIf(pending -> emit(pending.payload(), client) || pending.expiresAt() < now);
	}
	private static boolean emit(EefLifecyclePayload payload, Minecraft client) {
		if (client.level == null) return false;
		Entity entity = client.level.getEntity(payload.entityId());
		if (entity == null) return false;
		Entity source = payload.sourceId() < 0 ? null : client.level.getEntity(payload.sourceId());
		Map<String,Object> context = new LinkedHashMap<>();
		context.put("self", EefClientContext.entity(entity));
		Map<String,Object> sourceContext = source == null
			? Map.of("id", Integer.toString(payload.sourceId()), "type", payload.sourceType())
			: EefClientContext.entity(source);
		if (!payload.sourceType().isEmpty()) context.put("source", sourceContext);
		context.put("lifecycle", Map.of("event", payload.event(), "cause", payload.cause()));
		context.put("world", EefClientContext.world(client.level));
		context.put("calendar", EefCalendarContext.now());
		if (payload.event().equals("projectile_hit")) context.put("projectile", Map.of("type", payload.cause()));
		if (!payload.itemType().isEmpty()) context.put("item", Map.of("type", payload.itemType(),
			"enchanted", payload.itemEnchanted()));
		EntityEventKind kind = switch (payload.event()) {
			case "spawned" -> EntityEventKind.ENTITY_SPAWNED;
			case "transformed" -> EntityEventKind.ENTITY_TRANSFORMED;
			case "item_picked_up" -> EntityEventKind.ITEM_PICKED_UP;
			case "projectile_hit" -> EntityEventKind.PROJECTILE_HIT_ENTITY;
			default -> null;
		};
		if (kind == null) return true;
		EntityEventBus.emit(new EntityEvent(payload.gameTick(), kind, entity, source, entity.blockPosition(), null,
			EventReliability.SERVER_REQUIRED, context));
		return true;
	}
}
