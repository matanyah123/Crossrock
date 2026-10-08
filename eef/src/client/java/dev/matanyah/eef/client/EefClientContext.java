package dev.matanyah.eef.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/** Shared, pack-visible entity context. It contains generic state only. */
final class EefClientContext {
	private EefClientContext() {}

	static Map<String,Object> entity(Entity entity) {
		Map<String,Object> value = new LinkedHashMap<>();
		value.put("id", entity.getUUID().toString());
		value.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
		value.put("x", entity.getX()); value.put("y", entity.getY()); value.put("z", entity.getZ());
		value.put("on_fire", entity.isOnFire()); value.put("sneaking", entity.isCrouching()); value.put("swimming", entity.isSwimming());
		value.put("sprinting", entity.isSprinting()); value.put("moving", entity.getDeltaMovement().lengthSqr() > 0.0004);
		value.put("on_ground", entity.onGround());
		value.put("in_wall", entity.isInWall());
		value.put("passenger", entity.isPassenger()); value.put("alive", entity.isAlive());
		value.put("living", entity instanceof LivingEntity); value.put("mob", entity instanceof Mob);
		value.put("tags", entity.entityTags().stream().sorted().toList());
		if (entity.hasCustomName() && entity.getCustomName() != null) value.put("custom_name", entity.getCustomName().getString());
		if (entity.getVehicle() != null) value.put("vehicle", vehicle(entity.getVehicle()));
		value.put("passengers", entity.getPassengers().stream().map(EefClientContext::identity).toList());
		value.put("passenger_count", entity.getPassengers().size());
		value.put("environment", environment(entity));
		if (EntityEventFeaturesClient.RULE_ENGINE.requiresContextPrefix("self.nearby_entity_type_counts"))
			value.put("nearby_entity_type_counts", nearbyEntityTypeCounts(entity));
		value.put("ai", EefActivityClientState.context(entity));
		if (entity instanceof LivingEntity living) {
			value.put("fall_flying", living.isFallFlying());
			value.put("health", living.getHealth()); value.put("max_health", living.getMaxHealth()); value.put("baby", living.isBaby());
			value.put("health_ratio", living.getMaxHealth() <= 0 ? 0 : living.getHealth() / living.getMaxHealth());
			List<String> effects = effects(living); value.put("effects", effects); value.put("effect_count", effects.size());
			value.put("using_item", living.isUsingItem()); value.put("held_item", itemDetails(living.getMainHandItem()));
			putArmorContext(value, living);
			value.put("equipment_details", Map.of(
				"main_hand", itemDetails(living.getMainHandItem()), "off_hand", itemDetails(living.getOffhandItem()),
				"head", itemDetails(living.getItemBySlot(EquipmentSlot.HEAD)), "chest", itemDetails(living.getItemBySlot(EquipmentSlot.CHEST)),
				"legs", itemDetails(living.getItemBySlot(EquipmentSlot.LEGS)), "feet", itemDetails(living.getItemBySlot(EquipmentSlot.FEET))));
		}
		if (entity instanceof Player player) {
			value.put("creative", player.isCreative()); value.put("spectator", player.isSpectator());
			value.put("experience_level", player.experienceLevel);
		}
		if (entity instanceof Mob mob && mob.getTarget() != null) value.put("attack_target", identity(mob.getTarget()));
		if (entity instanceof TamableAnimal tamable) {
			value.put("tamed", tamable.isTame());
			if (tamable.getOwnerReference() != null) value.put("owner_id", tamable.getOwnerReference().getUUID().toString());
		}
		value.putAll(state(entity));
		return Collections.unmodifiableMap(value);
	}

	static Map<String,Object> world(Level level) {
		long timeOfDay = Math.floorMod(level.getOverworldClockTime(), 24000L);
		String period = timeOfDay < 6000 ? "morning" : timeOfDay < 12000 ? "afternoon" : timeOfDay < 13000 ? "evening" : "night";
		Map<String,Object> result = new LinkedHashMap<>();
		result.put("dimension", level.dimension().identifier().toString()); result.put("game_time", level.getGameTime());
		result.put("day_time", level.getOverworldClockTime()); result.put("time_of_day", timeOfDay); result.put("period", period);
		result.put("difficulty", level.getDifficulty().getSerializedName()); result.put("hardcore", level.getLevelData().isHardcore());
		result.put("raining", level.isRaining()); result.put("thundering", level.isThundering());
		return Collections.unmodifiableMap(result);
	}

	static Map<String,Object> state(Entity entity) {
		Map<String,Object> value = new LinkedHashMap<>();
		value.put("on_fire", entity.isOnFire());
		value.put("sneaking", entity.isCrouching());
		value.put("sprinting", entity.isSprinting());
		value.put("swimming", entity.isSwimming());
		value.put("moving", entity.getDeltaMovement().lengthSqr() > 0.0004);
		value.put("on_ground", entity.onGround());
		value.put("in_wall", entity.isInWall());
		value.put("pose", entity.getPose().name().toLowerCase(Locale.ROOT));
		value.put("passenger", entity.isPassenger());
		value.put("passenger_count", entity.getPassengers().size());
		value.put("tags", entity.entityTags().stream().sorted().toList());
		if (entity.hasCustomName() && entity.getCustomName() != null) value.put("custom_name", entity.getCustomName().getString());
		if (entity.getVehicle() != null) value.put("vehicle", vehicle(entity.getVehicle()));
		if (entity instanceof LivingEntity living) {
			value.put("fall_flying", living.isFallFlying());
			value.put("baby", living.isBaby());
			value.put("sleeping", living.isSleeping());
			value.put("using_item", living.isUsingItem());
			List<String> effects = effects(living); value.put("effects", effects); value.put("effect_count", effects.size());
			value.put("held_item", itemDetails(living.getMainHandItem()));
			putArmorContext(value, living);
			value.put("equipment", Map.of(
				"main_hand", item(living.getMainHandItem()), "off_hand", item(living.getOffhandItem()),
				"head", item(living.getItemBySlot(EquipmentSlot.HEAD)), "chest", item(living.getItemBySlot(EquipmentSlot.CHEST)),
				"legs", item(living.getItemBySlot(EquipmentSlot.LEGS)), "feet", item(living.getItemBySlot(EquipmentSlot.FEET))));
			value.put("equipment_details", Map.of(
				"main_hand", itemDetails(living.getMainHandItem()), "off_hand", itemDetails(living.getOffhandItem()),
				"head", itemDetails(living.getItemBySlot(EquipmentSlot.HEAD)), "chest", itemDetails(living.getItemBySlot(EquipmentSlot.CHEST)),
				"legs", itemDetails(living.getItemBySlot(EquipmentSlot.LEGS)), "feet", itemDetails(living.getItemBySlot(EquipmentSlot.FEET))));
		}
		if (entity instanceof Mob mob && mob.getTarget() != null) value.put("attack_target", identity(mob.getTarget()));
		if (entity instanceof TamableAnimal tamable) {
			value.put("tamed", tamable.isTame());
			if (tamable.getOwnerReference() != null) value.put("owner_id", tamable.getOwnerReference().getUUID().toString());
		}
		if (entity instanceof Sheep sheep) value.put("sheared", sheep.isSheared());
		if (entity instanceof Bee bee) value.put("angry", bee.getPersistentAngerTarget() != null);
		if (entity instanceof Villager villager) {
			var data = villager.getVillagerData();
			value.put("profession", data.profession().unwrapKey().map(key -> key.identifier().toString()).orElse("unknown"));
			value.put("villager_level", data.level());
		}
		if (entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager merchant) {
			value.put("trading", merchant.isTrading()); value.put("merchant_xp", merchant.getVillagerXp());
		}
		return Collections.unmodifiableMap(value);
	}

	private static Map<String,Object> identity(Entity entity) {
		return Map.of("id", entity.getUUID().toString(), "type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
	}

	private static Map<String,Object> vehicle(Entity entity) {
		Map<String,Object> result = new LinkedHashMap<>(identity(entity));
		result.put("x", entity.getX()); result.put("y", entity.getY()); result.put("z", entity.getZ());
		result.put("moving", entity.getDeltaMovement().lengthSqr() > 0.0004);
		result.put("speed", entity.getDeltaMovement().length());
		result.put("on_ground", entity.onGround()); result.put("in_water", entity.isInWater());
		result.put("passenger_count", entity.getPassengers().size());
		result.put("passengers", entity.getPassengers().stream().map(EefClientContext::identity).toList());
		return Collections.unmodifiableMap(result);
	}

	private static Map<String,Integer> nearbyEntityTypeCounts(Entity entity) {
		Map<String,Integer> counts = new LinkedHashMap<>();
		var area = entity.getBoundingBox().inflate(16.0, 8.0, 16.0);
		for (Entity nearby : entity.level().getEntities(entity, area, candidate -> candidate != entity))
			counts.merge(BuiltInRegistries.ENTITY_TYPE.getKey(nearby.getType()).toString(), 1, Integer::sum);
		return Collections.unmodifiableMap(counts);
	}

	private static String item(net.minecraft.world.item.ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static List<String> effects(LivingEntity living) {
		return living.getActiveEffects().stream().map(effect -> effect.getEffect().unwrapKey()
			.map(key -> key.identifier().toString()).orElse("unknown")).sorted().toList();
	}

	private static List<String> armorTypes(LivingEntity living) {
		List<String> result = new ArrayList<>();
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			ItemStack stack = living.getItemBySlot(slot);
			String type = item(stack);
			if (!stack.isEmpty() && armorMaterialForType(type).isPresent()) result.add(type);
		}
		return List.copyOf(result);
	}

	static Optional<String> armorMaterialForType(String type) {
		if (type == null) return Optional.empty();
		String path = type.substring(type.indexOf(':') + 1);
		for (String suffix : List.of("_helmet", "_chestplate", "_leggings", "_boots"))
			if (path.endsWith(suffix) && path.length() > suffix.length())
				return Optional.of(path.substring(0, path.length() - suffix.length()));
		return Optional.empty();
	}

	private static void putArmorContext(Map<String,Object> value, LivingEntity living) {
		List<String> types = armorTypes(living);
		Set<String> result = new LinkedHashSet<>();
		Map<String,Integer> counts = new LinkedHashMap<>();
		for (String type : types) {
			String material = armorMaterialForType(type).orElseThrow();
			result.add(material); counts.merge(material, 1, Integer::sum);
		}
		List<String> materials = List.copyOf(result);
		value.put("armor_count", types.size()); value.put("armor_types", types);
		value.put("armor_material_count", materials.size()); value.put("armor_materials", materials);
		value.put("armor_material_counts", Collections.unmodifiableMap(counts));
		value.put("armor_mixed", materials.size() > 1);
		if (types.size() == 4 && materials.size() == 1) value.put("full_armor_material", materials.getFirst());
	}

	private static Map<String,Object> itemDetails(ItemStack stack) {
		Map<String,Object> result = new LinkedHashMap<>();
		result.put("type", item(stack)); result.put("empty", stack.isEmpty()); result.put("count", stack.getCount());
		result.put("enchanted", stack.isEnchanted()); result.put("enchantment_count", stack.getEnchantments().size());
		result.put("consumable", stack.has(DataComponents.CONSUMABLE)); result.put("food", stack.has(DataComponents.FOOD));
		if (stack.getCustomName() != null) result.put("custom_name", stack.getCustomName().getString());
		result.put("damageable", stack.isDamageableItem());
		if (stack.isDamageableItem()) {
			int maximum = stack.getMaxDamage(), remaining = maximum - stack.getDamageValue();
			result.put("damage", stack.getDamageValue()); result.put("max_damage", maximum);
			result.put("durability_remaining", remaining);
			result.put("durability_fraction", maximum <= 0 ? 0 : (double) remaining / maximum);
		}
		return Collections.unmodifiableMap(result);
	}

	private static Map<String,Object> environment(Entity entity) {
		Level level = entity.level();
		var position = entity.blockPosition();
		BlockState feet = level.getBlockState(position), below = level.getBlockState(position.below());
		BlockState surface = feet.getCollisionShape(level, position).isEmpty() ? below : feet;
		Map<String,Object> result = new LinkedHashMap<>();
		result.put("in_water", entity.isInWater()); result.put("under_water", entity.isUnderWater());
		result.put("can_see_sky", level.canSeeSky(position)); result.put("raining_at", level.isRainingAt(position));
		result.put("block_at_feet", item(feet)); result.put("block_below", item(below)); result.put("surface_block", item(surface));
		var biome = level.getBiome(position);
		result.put("biome", biome.unwrapKey().map(key -> key.identifier().toString()).orElse("unknown"));
		result.put("temperature", biome.value().getBaseTemperature());
		result.put("dimension", level.dimension().identifier().toString());
		result.put("y", entity.getY());
		if (EntityEventFeaturesClient.RULE_ENGINE.requiresContextPrefix("self.environment.nearby_blocks")) {
			Set<String> nearby = new LinkedHashSet<>();
			for (var scan : net.minecraft.core.BlockPos.betweenClosed(position.offset(-6, -3, -6), position.offset(6, 3, 6))) {
				BlockState state = level.getBlockState(scan);
				if (!state.isAir()) nearby.add(item(state));
			}
			result.put("nearby_blocks", List.copyOf(nearby));
		}
		return Collections.unmodifiableMap(result);
	}

	private static String item(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}
}
