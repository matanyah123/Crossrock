package dev.matanyah.eef.client;

import dev.matanyah.eef.EntityEvent;
import dev.matanyah.eef.EntityEventBus;
import dev.matanyah.eef.EntityEventFeatures;
import dev.matanyah.eef.EntityEventKind;
import dev.matanyah.eef.EventReliability;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EntityEventFeaturesClient implements ClientModInitializer {
	public static final dev.matanyah.eef.EefRuleEngine RULE_ENGINE = new dev.matanyah.eef.EefRuleEngine();
	private static volatile long rulesReloadGeneration;
	public static final dev.matanyah.eef.EefConfig CONFIG = dev.matanyah.eef.EefConfig.load(
		FabricLoader.getInstance().getConfigDir().resolve("entity_event_features.properties"));
	@Override
	public void onInitializeClient() {
		RULE_ENGINE.setActionHandler(new EefClientActions());
		RULE_ENGINE.setSubjectResolver(new EefClientSubjectResolver());
		RULE_ENGINE.setRulePreferences(CONFIG);
		RULE_ENGINE.setCooldownMultiplier(CONFIG.cooldownMultiplier());
		EntityEventBus.EVENT.register(RULE_ENGINE);
		EefResourceReloader.register(RULE_ENGINE);
		EefSubtitleOverlay.register();
		EefSpeechBubbleOverlay.register();
		EefClientInteractions.register();
		EefAuthoritativeClientBridge.register();
		EefActivityClientState.register();
		EefTradeClientBridge.register();
		EefLifecycleClientBridge.register();
		EefCommandClient.register();
		ClientTickEvents.END_CLIENT_TICK.register(new Observer());
	}
	static void rulesReloaded() { rulesReloadGeneration++; EefCommandClient.catalogChanged(); }

	private static final class Observer implements ClientTickEvents.EndTick {
		private boolean wasFlying;
		private boolean wasSneaking;
		private boolean wasSwimming;
		private double lastX;
		private double lastY;
		private double lastZ;
		private boolean initialized;
		private boolean wasRaining;
		private boolean wasThundering;
		private long lastTimeBucket;
		private long lastDayTime;
		private long lastObservationBucket;
		private String lastGameMode;
		private String lastDifficulty;
		private java.util.UUID lastVehicle;
		private net.minecraft.core.BlockPos lastBlockBelowPosition;
		private net.minecraft.world.level.block.state.BlockState lastBlockBelow;
		private net.minecraft.client.multiplayer.ClientLevel observedLevel;
		private final java.util.Set<java.util.UUID> knownEntities = new java.util.HashSet<>();
		private final java.util.Map<java.util.UUID, Float> knownHealth = new java.util.HashMap<>();
		private final java.util.Map<java.util.UUID, Double> knownDistances = new java.util.HashMap<>();
		private final java.util.Map<java.util.UUID, Map<String,Object>> knownStates = new java.util.HashMap<>();
		private long seenRulesReloadGeneration = Long.MIN_VALUE;

		@Override
		public void onEndTick(Minecraft client) {
			Player player = client.player;
			if (player == null || client.level == null) {
				initialized = false;
				observedLevel = null;
				return;
			}
			if (observedLevel != client.level) {
				observedLevel = client.level;
				RULE_ENGINE.resetRuntimeState();
				EefSpeechBubbleOverlay.clear();
				EefAuthoritativeClientBridge.clear();
				EefActivityClientState.clear();
				EefTradeClientBridge.clear();
				EefLifecycleClientBridge.clear();
				knownEntities.clear();
				knownHealth.clear();
				knownDistances.clear();
				knownStates.clear();
				long tick = client.level.getGameTime();
				EntityEventBus.emit(event(client, tick, EntityEventKind.WORLD_LOADED, player, null, EventReliability.CLIENT_OBSERVED, Map.of()));
				EntityEventBus.emit(event(client, tick, EntityEventKind.PLAYER_JOINED, player, null, EventReliability.CLIENT_OBSERVED, Map.of()));
			}
			if (!initialized) {
				lastX = player.getX(); lastY = player.getY(); lastZ = player.getZ();
				wasFlying = player.getAbilities().flying; wasSneaking = player.isCrouching();
				wasSwimming = player.isSwimming(); wasRaining = client.level.isRaining(); wasThundering = client.level.isThundering();
				lastDayTime = client.level.getOverworldClockTime(); lastTimeBucket = lastDayTime / 20; lastObservationBucket = Long.MIN_VALUE;
				lastVehicle = player.getVehicle() == null ? null : player.getVehicle().getUUID();
				lastBlockBelowPosition = player.blockPosition().below();
				lastBlockBelow = client.level.getBlockState(lastBlockBelowPosition);
				lastGameMode = gameMode(client); lastDifficulty = client.level.getDifficulty().getSerializedName();
				initialized = true;
			}
			long tick = client.level.getGameTime();
			if (seenRulesReloadGeneration != rulesReloadGeneration) {
				seenRulesReloadGeneration = rulesReloadGeneration;
				lastObservationBucket = Long.MIN_VALUE;
				knownStates.clear();
			}
			RULE_ENGINE.tick(tick);
			EefAuthoritativeClientBridge.tick(tick);
			boolean observationPulse = tick / 20 != lastObservationBucket;
			boolean captureLoaded = RULE_ENGINE.captures(EntityEventKind.ENTITY_LOADED);
			boolean captureObserved = observationPulse && RULE_ENGINE.captures(EntityEventKind.ENTITY_OBSERVED);
			boolean captureDamage = RULE_ENGINE.captures(EntityEventKind.ENTITY_DAMAGED);
			boolean captureDeath = RULE_ENGINE.captures(EntityEventKind.ENTITY_DIED);
			boolean captureRemoved = RULE_ENGINE.captures(EntityEventKind.ENTITY_REMOVED);
			java.util.Set<Double> proximityThresholds = RULE_ENGINE.proximityThresholds();
			java.util.Set<java.util.UUID> present = new java.util.HashSet<>();
			for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
				present.add(entity.getUUID());
				if (knownEntities.add(entity.getUUID()) && captureLoaded) {
					EntityEventBus.emit(event(client, tick, EntityEventKind.ENTITY_LOADED, entity, null, EventReliability.CLIENT_OBSERVED, Map.of()));
				}
				if ((captureDamage || captureDeath) && entity instanceof net.minecraft.world.entity.LivingEntity living) {
					Float previous = knownHealth.put(entity.getUUID(), living.getHealth());
					if (captureDamage && previous != null && living.getHealth() < previous && living.getHealth() > 0f) EefAuthoritativeClientBridge.queueInferred(event(client, tick, EntityEventKind.ENTITY_DAMAGED,
						entity, null, EventReliability.CLIENT_INFERRED, Map.of("damage", Map.of("amount", previous - living.getHealth(), "previous_health", previous, "health", living.getHealth(), "authoritative", false))));
					if (captureDeath && living.getHealth() <= 0f && previous != null && previous > 0f) EefAuthoritativeClientBridge.queueInferred(event(client, tick, EntityEventKind.ENTITY_DIED,
						entity, null, EventReliability.CLIENT_OBSERVED, Map.of()));
				} else knownHealth.remove(entity.getUUID());
				String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
				if (RULE_ENGINE.observesEntityState(entityType)) {
					Map<String,Object> currentState = EefClientContext.state(entity);
					Map<String,Object> previousState = knownStates.put(entity.getUUID(), currentState);
					if (previousState != null) emitStateChanges(client, tick, entity, previousState, currentState);
				} else knownStates.remove(entity.getUUID());
				if (captureObserved)
					EntityEventBus.emit(event(client, tick, EntityEventKind.ENTITY_OBSERVED, entity, null, EventReliability.CLIENT_OBSERVED, Map.of()));
				if (entity != player && !proximityThresholds.isEmpty()) {
					double distance = player.distanceTo(entity);
					Double previousDistance = knownDistances.put(entity.getUUID(), distance);
					for (double threshold : proximityThresholds) {
						boolean entered = distance <= threshold && (previousDistance == null || previousDistance > threshold);
						boolean exited = previousDistance != null && previousDistance <= threshold && distance > threshold;
						if (entered || exited) EntityEventBus.emit(event(client, tick,
							entered ? EntityEventKind.ENTITY_ENTERED_RANGE : EntityEventKind.ENTITY_EXITED_RANGE,
							entity, player, EventReliability.CLIENT_OBSERVED,
							Map.of("distance", Map.of("observer_to_self", distance,
								"previous_observer_to_self", previousDistance == null ? distance : previousDistance,
								"threshold", threshold))));
					}
				} else knownDistances.remove(entity.getUUID());
			}
			for (java.util.UUID id : new java.util.HashSet<>(knownEntities)) if (!present.contains(id)) {
				knownEntities.remove(id); knownHealth.remove(id); knownDistances.remove(id); knownStates.remove(id);
				if (captureRemoved) EntityEventBus.emit(event(client, tick, EntityEventKind.ENTITY_REMOVED, player, null, EventReliability.CLIENT_INFERRED, Map.of("removed_entity_id", id.toString())));
			}
			if (player.getX() != lastX || player.getY() != lastY || player.getZ() != lastZ) {
				EntityEventBus.emit(event(client, tick, EntityEventKind.PLAYER_MOVED, player, null, EventReliability.CLIENT_OBSERVED,
					Map.of("movement", Map.of("delta_x", player.getX()-lastX, "delta_y", player.getY()-lastY, "delta_z", player.getZ()-lastZ))));
			}
			boolean flying = player.getAbilities().flying;
			if (flying != wasFlying) emit(tick, flying ? EntityEventKind.PLAYER_STARTED_FLYING : EntityEventKind.PLAYER_STOPPED_FLYING, player);
			boolean sneaking = player.isCrouching();
			if (sneaking != wasSneaking) emit(tick, sneaking ? EntityEventKind.PLAYER_STARTED_SNEAKING : EntityEventKind.PLAYER_STOPPED_SNEAKING, player);
			boolean swimming = player.isSwimming();
			if (swimming != wasSwimming) emit(tick, swimming ? EntityEventKind.PLAYER_STARTED_SWIMMING : EntityEventKind.PLAYER_STOPPED_SWIMMING, player);
			if (player.getDeltaMovement().y > 0.1 && !player.onGround() && player.getY() > lastY + 0.05) {
				EntityEventBus.emit(event(client, tick, EntityEventKind.PLAYER_JUMPED, player, null, EventReliability.CLIENT_INFERRED, Map.of()));
			}
			Entity vehicle = player.getVehicle(); java.util.UUID vehicleId = vehicle == null ? null : vehicle.getUUID();
			if (!java.util.Objects.equals(vehicleId, lastVehicle)) {
				if (vehicle != null) EntityEventBus.emit(event(client, tick, EntityEventKind.RIDING_STARTED, player, vehicle, EventReliability.CLIENT_OBSERVED, Map.of()));
				else EntityEventBus.emit(event(client, tick, EntityEventKind.RIDING_STOPPED, player, null, EventReliability.CLIENT_OBSERVED, Map.of()));
			}
			net.minecraft.core.BlockPos blockBelowPosition = player.blockPosition().below();
			net.minecraft.world.level.block.state.BlockState blockBelow = client.level.getBlockState(blockBelowPosition);
			if (lastBlockBelow != null && blockBelowPosition.equals(lastBlockBelowPosition) && !lastBlockBelow.equals(blockBelow))
				EefClientInteractions.onEnvironmentalBlockTransition(client.level, player, blockBelowPosition, lastBlockBelow, blockBelow);
			long dayTime = client.level.getOverworldClockTime(), timeBucket = dayTime / 20;
			if (timeBucket != lastTimeBucket) EntityEventBus.emit(event(client, tick, EntityEventKind.TIME_CHANGED,
				player, null, EventReliability.CLIENT_OBSERVED, Map.of("time", dev.matanyah.eef.EefTimeTransition.between(lastDayTime, dayTime))));
			if (client.level.isRaining() != wasRaining || client.level.isThundering() != wasThundering)
				EntityEventBus.emit(event(client, tick, EntityEventKind.WEATHER_CHANGED, player, null, EventReliability.CLIENT_OBSERVED, Map.of()));
			String gameMode = gameMode(client);
			if (!java.util.Objects.equals(gameMode, lastGameMode)) EntityEventBus.emit(event(client, tick,
				EntityEventKind.PLAYER_GAME_MODE_CHANGED, player, null, EventReliability.CLIENT_OBSERVED,
				Map.of("change", Map.of("path", "game_mode", "before", lastGameMode, "after", gameMode),
					"player", Map.of("game_mode", gameMode))));
			String difficulty = client.level.getDifficulty().getSerializedName();
			if (!java.util.Objects.equals(difficulty, lastDifficulty)) EntityEventBus.emit(event(client, tick,
				EntityEventKind.WORLD_DIFFICULTY_CHANGED, player, null, EventReliability.CLIENT_OBSERVED,
				Map.of("change", Map.of("path", "difficulty", "before", lastDifficulty, "after", difficulty))));
			lastX = player.getX(); lastY = player.getY(); lastZ = player.getZ();
			wasFlying = flying; wasSneaking = sneaking; wasSwimming = swimming; lastVehicle = vehicleId;
			lastBlockBelowPosition = blockBelowPosition; lastBlockBelow = blockBelow;
			lastDayTime = dayTime; lastTimeBucket = timeBucket; wasRaining = client.level.isRaining(); wasThundering = client.level.isThundering();
			lastGameMode = gameMode; lastDifficulty = difficulty;
			lastObservationBucket = tick / 20;
		}

		private static void emit(long tick, EntityEventKind kind, Player player) {
			Minecraft client = Minecraft.getInstance();
			EntityEventBus.emit(event(client, tick, kind, player, null, EventReliability.CLIENT_OBSERVED, Map.of()));
		}

		private static String gameMode(Minecraft client) {
			return client.gameMode == null ? "unknown" : client.gameMode.getPlayerMode().getName();
		}

		private static void emitStateChanges(Minecraft client, long tick, Entity entity,
				Map<String,Object> before, Map<String,Object> after) {
			for (dev.matanyah.eef.EefStateDiff.Change difference : dev.matanyah.eef.EefStateDiff.between(before, after)) {
				Map<String,Object> change = new LinkedHashMap<>();
				change.put("path", difference.path()); change.put("before_exists", difference.beforeExists()); change.put("after_exists", difference.afterExists());
				if (difference.beforeExists()) change.put("before", difference.before());
				if (difference.afterExists()) change.put("after", difference.after());
				EntityEventBus.emit(event(client, tick, EntityEventKind.ENTITY_STATE_CHANGED, entity, null,
					EventReliability.CLIENT_OBSERVED, Map.of("snapshot", Map.of("before", before, "after", after), "change", change)));
			}
		}

		private static EntityEvent event(Minecraft client, long tick, EntityEventKind kind, Entity self, Entity target,
				EventReliability reliability, Map<String,Object> extra) {
			Map<String,Object> context = new LinkedHashMap<>(extra);
			context.put("self", actor(self));
			if (target != null) context.put("target", actor(target));
			if (client.player != null) {
				context.put("observer", actor(client.player));
				if (self != null) {
					context.putIfAbsent("distance", Map.of("observer_to_self", client.player.distanceTo(self)));
					var eye = client.player.getEyePosition();
					var toward = self.getBoundingBox().getCenter().subtract(eye);
					double length = toward.length();
					double dot = length <= 1.0e-6 ? 1.0 : client.player.getLookAngle().dot(toward.scale(1.0 / length));
					double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
					context.put("gaze", Map.of("observer_to_self_angle", angle, "observer_looking_at_self", angle <= 8.0));
				}
			}
			if (client.level != null) {
				long timeOfDay = Math.floorMod(client.level.getOverworldClockTime(), 24000L);
				String period = timeOfDay < 6000 ? "morning" : timeOfDay < 12000 ? "afternoon" : timeOfDay < 13000 ? "evening" : "night";
				Map<String,Object> world = new LinkedHashMap<>();
				world.put("dimension", client.level.dimension().identifier().toString());
				world.put("game_time", client.level.getGameTime()); world.put("day_time", client.level.getOverworldClockTime());
				world.put("time_of_day", timeOfDay); world.put("period", period);
				world.put("difficulty", client.level.getDifficulty().getSerializedName());
				world.put("hardcore", client.level.getLevelData().isHardcore());
				world.put("raining", client.level.isRaining()); world.put("thundering", client.level.isThundering());
				context.put("world", java.util.Collections.unmodifiableMap(world));
				context.put("calendar", dev.matanyah.eef.EefCalendarContext.now());
			}
			return new EntityEvent(tick, kind, self, target, self == null ? null : self.blockPosition(), null, reliability, context);
		}

		private static Map<String,Object> actor(Entity entity) {
			return EefClientContext.entity(entity);
		}
	}
}
