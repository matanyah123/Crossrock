package dev.matanyah.eef.client;

import dev.matanyah.eef.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.*;

/** Receives server truth and holds inferred events briefly to prevent duplicate dialogue. */
public final class EefAuthoritativeClientBridge {
	private static final EefAuthoritativeEventGate GATE = new EefAuthoritativeEventGate();
	private EefAuthoritativeClientBridge() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(EefDamagePayload.TYPE, (payload, context) ->
			context.client().execute(() -> receive(payload, context.client())));
	}

	public static void queueInferred(EntityEvent event) {
		GATE.defer(event, 2);
	}

	public static void tick(long gameTick) {
		for (EntityEvent event : GATE.drain(gameTick)) EntityEventBus.emit(new EntityEvent(
			gameTick, event.kind(), event.source(), event.target(), event.position(), event.detail(), event.reliability(), event.context()));
	}

	public static void clear() { GATE.clear(); }

	private static void receive(EefDamagePayload payload, Minecraft client) {
		if (client.level == null) return;
		Entity victim = client.level.getEntity(payload.victimId());
		Entity attacker = payload.attackerId() < 0 ? null : client.level.getEntity(payload.attackerId());
		Entity directEntity = payload.directEntityId() < 0 ? null : client.level.getEntity(payload.directEntityId());
		EntityEventKind kind = payload.death() ? EntityEventKind.ENTITY_DIED : EntityEventKind.ENTITY_DAMAGED;
		String victimKey = victim == null ? "network:" + payload.victimId() : victim.getUUID().toString();
		long clientTick = client.level.getGameTime();
		GATE.authoritative(kind, victimKey, clientTick + 5);

		Map<String,Object> context = new LinkedHashMap<>();
		context.put("self", victim == null ? fallback(payload.victimId(), payload.victimType(), payload.x(), payload.y(), payload.z()) : EefClientContext.entity(victim));
		if (attacker != null) context.put("attacker", EefClientContext.entity(attacker));
		else if (!payload.attackerType().isEmpty()) context.put("attacker", fallback(payload.attackerId(), payload.attackerType(), 0, 0, 0));
		if (directEntity != null) context.put("direct_entity", EefClientContext.entity(directEntity));
		else if (!payload.directEntityType().isEmpty()) context.put("direct_entity", fallback(payload.directEntityId(), payload.directEntityType(), 0, 0, 0));
		Map<String,Object> damage = new LinkedHashMap<>();
		damage.put("amount", payload.amount()); damage.put("base_amount", payload.baseAmount()); damage.put("blocked", payload.blocked());
		damage.put("type", payload.damageType()); damage.put("message_id", payload.messageId()); damage.put("direct", payload.direct());
		damage.put("authoritative", true); damage.put("server_tick", payload.gameTick());
		if (!payload.weaponType().isEmpty()) damage.put("weapon_type", payload.weaponType());
		context.put("damage", Collections.unmodifiableMap(damage));
		if (client.player != null) {
			context.put("observer", EefClientContext.entity(client.player));
			if (victim != null) context.put("distance", Map.of("observer_to_self", client.player.distanceTo(victim)));
		}
		context.put("world", EefClientContext.world(client.level));
		context.put("calendar", EefCalendarContext.now());
		Identifier detail;
		try { detail = Identifier.parse(payload.damageType()); } catch (RuntimeException ignored) { detail = null; }
		BlockPos position = victim == null ? BlockPos.containing(payload.x(), payload.y(), payload.z()) : victim.blockPosition();
		EntityEventBus.emit(new EntityEvent(clientTick, kind, victim, attacker, position, detail,
			EventReliability.SERVER_REQUIRED, context));
	}

	private static Map<String,Object> fallback(int id, String type, double x, double y, double z) {
		return Map.of("id", "network:" + id, "type", type, "x", x, "y", y, "z", z);
	}
}
