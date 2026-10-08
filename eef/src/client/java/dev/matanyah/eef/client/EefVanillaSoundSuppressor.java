package dev.matanyah.eef.client;

import dev.matanyah.eef.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/** Matches replaceable vanilla entity sounds against loaded EEF dialogue providers. */
public final class EefVanillaSoundSuppressor {
	private EefVanillaSoundSuppressor() {}

	public static boolean shouldSuppress(SoundInstance sound) {
		if (!sound.getIdentifier().getNamespace().equals("minecraft")) return false;
		String soundPath = sound.getIdentifier().getPath();
		boolean damage = soundPath.contains(".hurt");
		boolean ambient = soundPath.equals("entity.villager.ambient") || soundPath.equals("entity.wandering_trader.ambient");
		if (!damage && !ambient) return false;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return false;
		Vec3 position = new Vec3(sound.getX(), sound.getY(), sound.getZ());
		Entity nearest = client.level.getEntities((Entity) null, AABB.ofSize(position, 4, 4, 4), entity -> entity instanceof LivingEntity)
			.stream().min(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(position))).orElse(null);
		if (nearest == null || nearest.distanceToSqr(position) > 4) return false;
		String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(nearest.getType()).toString();
		if (ambient) return EntityEventFeaturesClient.RULE_ENGINE.hasSoundProvider(EntityEventKind.ENTITY_OBSERVED, entityType);
		return EntityEventFeaturesClient.RULE_ENGINE.hasMatchingSoundReplacement(previewEvent(client, nearest));
	}

	private static EntityEvent previewEvent(Minecraft client, Entity self) {
		Map<String,Object> actor = new LinkedHashMap<>();
		actor.put("id", self.getUUID().toString());
		actor.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(self.getType()).toString());
		actor.put("x", self.getX()); actor.put("y", self.getY()); actor.put("z", self.getZ());
		actor.put("on_fire", self.isOnFire()); actor.put("sneaking", self.isCrouching()); actor.put("swimming", self.isSwimming());
		actor.put("passenger", self.isPassenger());
		if (self.hasCustomName() && self.getCustomName() != null) actor.put("custom_name", self.getCustomName().getString());
		if (self instanceof LivingEntity living) {
			actor.put("health", living.getHealth()); actor.put("max_health", living.getMaxHealth()); actor.put("baby", living.isBaby());
		}
		Map<String,Object> context = new LinkedHashMap<>(); context.put("self", actor);
		context.put("damage", Map.of("amount", Double.MIN_VALUE));
		if (client.player != null) {
			context.put("observer", Map.of("id", client.player.getUUID().toString(), "type", "minecraft:player"));
			context.put("distance", Map.of("observer_to_self", client.player.distanceTo(self)));
		}
		return new EntityEvent(client.level.getGameTime(), EntityEventKind.ENTITY_DAMAGED, self, null,
			self.blockPosition(), null, EventReliability.CLIENT_INFERRED, context);
	}
}
