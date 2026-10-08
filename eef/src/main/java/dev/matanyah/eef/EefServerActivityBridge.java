package dev.matanyah.eef;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.core.GlobalPos;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Low-frequency generic activity snapshots for entities already tracked by each client. */
public final class EefServerActivityBridge {
	private EefServerActivityBridge() {}
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) return;
			for (var level : server.getAllLevels()) for (Entity entity : level.getAllEntities())
				if (entity instanceof LivingEntity living) send(living);
		});
	}
	private static void send(LivingEntity living) {
		LinkedHashSet<ServerPlayer> recipients = new LinkedHashSet<>(PlayerLookup.tracking(living));
		if (living instanceof ServerPlayer player) recipients.add(player);
		if (recipients.isEmpty()) return;
		var brain = living.getBrain();
		List<String> activities = brain.getActiveActivities().stream().map(activity -> activity.getName()).sorted().toList();
		String primary = brain.getActiveNonCoreActivity().map(activity -> activity.getName()).orElse("none");
		List<String> behaviors = brain.getRunningBehaviors().stream().map(behavior -> {
			String simple = behavior.getClass().getSimpleName();
			return simple.isBlank() ? behavior.debugString() : simple;
		}).distinct().sorted().toList();
		boolean navigating = living instanceof Mob mob && mob.getNavigation().isInProgress();
		boolean stuck = living instanceof Mob mob && mob.getNavigation().isStuck();
		Map<String,Integer> inventory = new LinkedHashMap<>();
		if (living instanceof Villager villager) {
			var container = villager.getInventory();
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				var stack = container.getItem(slot);
				if (!stack.isEmpty()) inventory.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
			}
		}
		Map<String,String> memoryDimensions = new LinkedHashMap<>();
		Map<String,Double> memoryDistances = new LinkedHashMap<>();
		if (living instanceof Villager villager) {
			putMemory(villager, "home", MemoryModuleType.HOME, memoryDimensions, memoryDistances);
			putMemory(villager, "job_site", MemoryModuleType.JOB_SITE, memoryDimensions, memoryDistances);
			putMemory(villager, "meeting_point", MemoryModuleType.MEETING_POINT, memoryDimensions, memoryDistances);
		}
		for (ServerPlayer recipient : recipients) if (ServerPlayNetworking.canSend(recipient, EefActivityPayload.TYPE)) {
			int reputation = living instanceof Villager villager ? villager.getPlayerReputation(recipient) : 0;
			ServerPlayNetworking.send(recipient, new EefActivityPayload(living.level().getGameTime(), living.getId(), primary,
				activities, behaviors, navigating, stuck, inventory, reputation, memoryDimensions, memoryDistances));
		}
	}

	private static void putMemory(Villager villager, String name, MemoryModuleType<GlobalPos> type,
			Map<String,String> dimensions, Map<String,Double> distances) {
		villager.getBrain().getMemory(type).ifPresent(position -> {
			dimensions.put(name, position.dimension().identifier().toString());
			if (position.dimension().equals(villager.level().dimension()))
				distances.put(name, Math.sqrt(villager.blockPosition().distSqr(position.pos())));
		});
	}
}
