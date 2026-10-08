package dev.matanyah.eef;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;

/** Optional authoritative capture. Client-only EEF continues to work without this bridge. */
public final class EefServerDamageBridge {
	private EefServerDamageBridge() {}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, amount, blocked) ->
			send(entity, source, amount, baseAmount, blocked, false));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> send(entity, source, 0, 0, false, true));
	}

	private static void send(LivingEntity victim, DamageSource source, float amount, float baseAmount, boolean blocked, boolean death) {
		Entity attacker = source.getEntity(), directEntity = source.getDirectEntity();
		ItemStack weapon = source.getWeaponItem();
		String damageType = source.typeHolder().unwrapKey().map(key -> key.identifier().toString()).orElse(source.getMsgId());
		EefDamagePayload payload = new EefDamagePayload(death, victim.level().getGameTime(), victim.getId(), type(victim),
			victim.getX(), victim.getY(), victim.getZ(), amount, baseAmount, blocked, damageType, source.getMsgId(), source.isDirect(),
			id(attacker), type(attacker), id(directEntity), type(directEntity),
			weapon == null || weapon.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(weapon.getItem()).toString());
		LinkedHashSet<ServerPlayer> recipients = new LinkedHashSet<>(PlayerLookup.tracking(victim));
		if (victim instanceof ServerPlayer player) recipients.add(player);
		for (ServerPlayer recipient : recipients) if (ServerPlayNetworking.canSend(recipient, EefDamagePayload.TYPE))
			ServerPlayNetworking.send(recipient, payload);
	}

	private static int id(Entity entity) { return entity == null ? -1 : entity.getId(); }
	private static String type(Entity entity) { return entity == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(); }
}
