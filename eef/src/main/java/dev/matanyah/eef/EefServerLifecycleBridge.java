package dev.matanyah.eef;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;

/** Sends generic spawn, transformation, breeding and pickup facts to tracking clients. */
public final class EefServerLifecycleBridge {
	private EefServerLifecycleBridge() {}
	public static void send(String event, Entity entity, Entity source, String cause, ItemStack item) {
		if (!(entity.level() instanceof ServerLevel level)) return;
		EefLifecyclePayload payload = new EefLifecyclePayload(event, level.getGameTime(), entity.getId(), type(entity),
			source == null ? -1 : source.getId(), type(source), cause == null ? "" : cause,
			item == null || item.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),
			item != null && item.isEnchanted());
		LinkedHashSet<ServerPlayer> recipients = new LinkedHashSet<>(PlayerLookup.tracking(entity));
		for (ServerPlayer player : level.players()) if (player.distanceToSqr(entity) <= 4096) recipients.add(player);
		for (ServerPlayer recipient : recipients) if (ServerPlayNetworking.canSend(recipient, EefLifecyclePayload.TYPE))
			ServerPlayNetworking.send(recipient, payload);
	}
	private static String type(Entity entity) {
		return entity == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}
}
