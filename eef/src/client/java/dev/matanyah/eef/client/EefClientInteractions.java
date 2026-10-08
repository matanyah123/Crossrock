package dev.matanyah.eef.client;

import dev.matanyah.eef.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

import java.util.*;

/** Client input capture plus short-lived block-state transition inference. */
public final class EefClientInteractions {
	private record PendingBlock(BlockState before, Player actor, String itemType, String hand, long expiresAt) {}
	private static final Map<BlockPos, PendingBlock> PENDING_BLOCKS = new HashMap<>();
	private static final Map<UUID, ArrayDeque<Long>> RECENT_BREAKS = new HashMap<>();
	private static Level observedLevel;
	private EefClientInteractions() {}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (level.isClientSide()) emitEntityInteraction(level, player, target, hand, "attack");
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (level.isClientSide()) emitEntityInteraction(level, player, target, hand, "use");
			return InteractionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, level, hand, position, direction) -> {
			if (level.isClientSide()) {
				watch(level, position, player, hand);
				emitBlock(level, player, position, hand, EntityEventKind.PLAYER_BREAKING_BLOCK, "attack");
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide()) {
				watch(level, hit.getBlockPos(), player, hand); watch(level, hit.getBlockPos().relative(hit.getDirection()), player, hand);
				emitBlock(level, player, hit.getBlockPos(), hand, EntityEventKind.PLAYER_INTERACTED_WITH_BLOCK, "use");
				emitBlock(level, player, hit.getBlockPos(), hand, EntityEventKind.ITEM_USE_ON_BLOCK_STARTED, "use_on_block");
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide()) emit(level, EntityEventKind.ITEM_USED, player, null, player.blockPosition(), hand,
				"use", Map.of());
			return InteractionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(EefClientInteractions::checkBlockTransitions);
	}

	/**
	 * Called after a client-predicted BlockItem placement succeeds. This is the primary
	 * placement signal; the pending block-state watches remain as a fallback for items
	 * whose placement is not implemented by BlockItem.
	 */
	public static void onBlockItemPlaced(BlockPlaceContext placement, InteractionResult result) {
		Level level = placement.getLevel();
		Player actor = placement.getPlayer();
		if (!level.isClientSide() || actor == null || !result.consumesAction()) return;
		BlockPos position = placement.getClickedPos();
		BlockState state = level.getBlockState(position);
		if (state.isAir()) return;

		// The direct signal supersedes a watch created by UseBlockCallback and prevents
		// the fallback poller from emitting the same placement a second time.
		PENDING_BLOCKS.remove(position);
		Map<String,Object> context = new LinkedHashMap<>();
		context.put("self", actor(actor));
		context.put("actor", actor(actor));
		context.put("block", block(state, position));
		context.put("neighborhood", neighborhood(level, position, 2));
		context.put("interaction", Map.of("action", "place", "hand", placement.getHand().name().toLowerCase(Locale.ROOT)));
		ItemStack stack = placement.getItemInHand();
		context.put("item", item(stack));
		EntityEventBus.emit(new EntityEvent(level.getGameTime(), EntityEventKind.BLOCK_PLACED,
			actor, null, position, null, EventReliability.CLIENT_INFERRED, context));
	}

	/** Emits a confirmed environmental block transition observed beneath the player. */
	static void onEnvironmentalBlockTransition(Level level, Player actor, BlockPos position, BlockState before, BlockState after) {
		Map<String,Object> context = new LinkedHashMap<>();
		context.put("self", actor(actor)); context.put("actor", actor(actor));
		context.put("previous_block", block(before, position)); context.put("block", block(after, position));
		context.put("interaction", Map.of("action", "environmental_transition", "hand", "none"));
		EntityEventBus.emit(new EntityEvent(level.getGameTime(), EntityEventKind.BLOCK_STATE_CHANGED, actor, null,
			position, null, EventReliability.CLIENT_INFERRED, context));
	}

	private static void emitEntityInteraction(Level level, Player actor, Entity self, InteractionHand hand, String action) {
		emit(level, EntityEventKind.ENTITY_INTERACTED, self, actor, self.blockPosition(), hand, action,
			Map.of("actor", actor(actor)));
	}

	private static void emitBlock(Level level, Player actor, BlockPos position, InteractionHand hand,
			EntityEventKind kind, String action) {
		BlockState state = level.getBlockState(position);
		emit(level, kind, actor, null, position, hand, action,
			Map.of("actor", actor(actor), "block", block(state, position)));
	}

	private static void emit(Level level, EntityEventKind kind, Entity self, Entity target, BlockPos position,
			InteractionHand hand, String action, Map<String,Object> extra) {
		Map<String,Object> context = new LinkedHashMap<>(extra);
		context.put("self", actor(self)); context.put("interaction", Map.of("action", action, "hand", hand.name().toLowerCase(Locale.ROOT)));
		if (target != null) context.put("target", actor(target));
		ItemStack stack = self instanceof Player player ? player.getItemInHand(hand)
			: target instanceof Player player ? player.getItemInHand(hand) : ItemStack.EMPTY;
		context.put("item", item(stack));
		EntityEventBus.emit(new EntityEvent(level.getGameTime(), kind, self, target, position,
			Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, action), EventReliability.CLIENT_OBSERVED, context));
	}

	private static Map<String,Object> item(ItemStack stack) {
		Map<String,Object> result = new LinkedHashMap<>();
		result.put("type", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		result.put("empty", stack.isEmpty()); result.put("count", stack.getCount());
		result.put("enchanted", stack.isEnchanted()); result.put("enchantment_count", stack.getEnchantments().size());
		result.put("consumable", stack.has(DataComponents.CONSUMABLE)); result.put("food", stack.has(DataComponents.FOOD));
		if (stack.getCustomName() != null) result.put("custom_name", stack.getCustomName().getString());
		return Collections.unmodifiableMap(result);
	}

	private static void watch(Level level, BlockPos position, Player actor, InteractionHand hand) {
		PENDING_BLOCKS.put(position.immutable(), new PendingBlock(level.getBlockState(position), actor,
			BuiltInRegistries.ITEM.getKey(actor.getItemInHand(hand).getItem()).toString(), hand.name().toLowerCase(Locale.ROOT),
			level.getGameTime() + 60));
	}

	private static void checkBlockTransitions(Minecraft client) {
		if (client.level == null) { observedLevel = null; PENDING_BLOCKS.clear(); RECENT_BREAKS.clear(); return; }
		if (observedLevel != client.level) { observedLevel = client.level; PENDING_BLOCKS.clear(); RECENT_BREAKS.clear(); return; }
		if (PENDING_BLOCKS.isEmpty()) return;
		long tick = client.level.getGameTime();
		Iterator<Map.Entry<BlockPos,PendingBlock>> iterator = PENDING_BLOCKS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<BlockPos,PendingBlock> entry = iterator.next(); PendingBlock pending = entry.getValue();
			BlockState current = client.level.getBlockState(entry.getKey());
			String transition = transition(pending.before(), current, pending.itemType());
			EntityEventKind kind = transition != null ? EntityEventKind.BLOCK_STATE_CHANGED
				: pending.before().isAir() && !current.isAir() ? EntityEventKind.BLOCK_PLACED
				: !pending.before().isAir() && current.isAir() ? EntityEventKind.BLOCK_BROKEN : null;
			if (kind != null) {
				Map<String,Object> context = new LinkedHashMap<>(); context.put("self", actor(pending.actor())); context.put("actor", actor(pending.actor()));
				context.put("block", block(current, entry.getKey())); context.put("previous_block", block(pending.before(), entry.getKey()));
				context.put("item", Map.of("type", pending.itemType(), "empty", pending.itemType().equals("minecraft:air")));
				context.put("interaction", Map.of("action", transition == null ? "state_change" : transition, "hand", pending.hand()));
				if (transition != null) context.put("transition", Map.of("action", transition));
				if (kind == EntityEventKind.BLOCK_PLACED) context.put("neighborhood", neighborhood(client.level, entry.getKey(), 2));
				if (kind == EntityEventKind.BLOCK_BROKEN) {
					ArrayDeque<Long> recent = RECENT_BREAKS.computeIfAbsent(pending.actor().getUUID(), ignored -> new ArrayDeque<>());
					while (!recent.isEmpty() && recent.getFirst() < tick - 40) recent.removeFirst();
					recent.addLast(tick);
					context.put("series", Map.of("count", recent.size(), "window_ticks", 40));
				}
				EntityEventBus.emit(new EntityEvent(tick, kind, pending.actor(), null, entry.getKey(), null,
					EventReliability.CLIENT_INFERRED, context));
				iterator.remove();
			} else if (tick > pending.expiresAt()) iterator.remove();
		}
	}

	private static String transition(BlockState before, BlockState after, String itemType) {
		String beforeType = BuiltInRegistries.BLOCK.getKey(before.getBlock()).toString();
		String afterType = BuiltInRegistries.BLOCK.getKey(after.getBlock()).toString();
		if (beforeType.equals("minecraft:tnt") && after.isAir()
				&& (itemType.equals("minecraft:flint_and_steel") || itemType.equals("minecraft:fire_charge"))) return "ignite";
		if (!beforeType.equals(afterType)) return null;
		Object oldLit = properties(before).get("lit"), newLit = properties(after).get("lit");
		if (oldLit instanceof Boolean oldValue && newLit instanceof Boolean newValue && oldValue != newValue)
			return newValue ? "light" : "extinguish";
		Object oldOpen = properties(before).get("open"), newOpen = properties(after).get("open");
		if (oldOpen instanceof Boolean oldValue
				&& newOpen instanceof Boolean newValue && oldValue != newValue) return newValue ? "open" : "close";
		return before.equals(after) ? null : "changed";
	}

	private static Map<String,Object> block(BlockState state, BlockPos position) {
		Map<String,Object> result = new LinkedHashMap<>();
		result.put("type", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		result.put("air", state.isAir()); result.put("replaceable", state.canBeReplaced());
		result.put("properties", properties(state));
		result.put("x", position.getX()); result.put("y", position.getY()); result.put("z", position.getZ());
		return Collections.unmodifiableMap(result);
	}

	private static Map<String,Object> properties(BlockState state) {
		Map<String,Object> result = new LinkedHashMap<>();
		if (state.hasProperty(BlockStateProperties.LIT)) result.put("lit", state.getValue(BlockStateProperties.LIT));
		if (state.hasProperty(BlockStateProperties.OPEN)) result.put("open", state.getValue(BlockStateProperties.OPEN));
		return Collections.unmodifiableMap(result);
	}

	private static Map<String,Object> neighborhood(Level level, BlockPos center, int radius) {
		Map<String,Integer> counts = new LinkedHashMap<>();
		for (BlockPos scan : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			BlockState state = level.getBlockState(scan);
			if (!state.isAir()) counts.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1, Integer::sum);
		}
		return Map.of("radius", radius, "block_counts", Collections.unmodifiableMap(counts));
	}

	private static Map<String,Object> actor(Entity entity) {
		return EefClientContext.entity(entity);
	}
}
