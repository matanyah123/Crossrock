package dev.matanyah.eef.client;

import dev.matanyah.eef.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.Merchant;

import java.util.*;

/** Client trade-screen lifecycle plus authoritative transaction delivery. */
public final class EefTradeClientBridge {
	private static boolean open;
	private static Entity activeMerchant;
	private static int completedTrades;
	private static int offerCount;
	private static final Map<Integer,MerchantContext> MERCHANT_CONTEXT = new HashMap<>();
	private EefTradeClientBridge() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(EefTradePayload.TYPE, (payload, context) ->
			context.client().execute(() -> receive(payload, context.client())));
		ClientPlayNetworking.registerGlobalReceiver(EefMerchantContextPayload.TYPE, (payload, context) ->
			context.client().execute(() -> receiveContext(payload, context.client())));
		ClientTickEvents.END_CLIENT_TICK.register(EefTradeClientBridge::tick);
	}
	public static void clear() { open = false; activeMerchant = null; completedTrades = 0; offerCount = 0; MERCHANT_CONTEXT.clear(); }

	private static void tick(Minecraft client) {
		boolean nowOpen = client.gui.screen() instanceof MerchantScreen;
		if (nowOpen && (!open || activeMerchant == null)) {
			activeMerchant = nearestMerchant(client); completedTrades = 0;
			offerCount = ((MerchantScreen)client.gui.screen()).getMenu().getOffers().size();
			if (activeMerchant != null) emitScreen(client, EntityEventKind.TRADE_SCREEN_OPENED);
		} else if (!nowOpen && open) {
			if (activeMerchant != null) emitScreen(client, EntityEventKind.TRADE_SCREEN_CLOSED);
			activeMerchant = null; completedTrades = 0; offerCount = 0;
		}
		open = nowOpen;
	}
	private static Entity nearestMerchant(Minecraft client) {
		if (client.level == null || client.player == null) return null;
		Entity nearest = null; double best = 64.0;
		for (Entity entity : client.level.entitiesForRendering()) if (entity instanceof Merchant) {
			double distance = entity.distanceToSqr(client.player);
			if (distance < best) { best = distance; nearest = entity; }
		}
		return nearest;
	}
	private static void emitScreen(Minecraft client, EntityEventKind kind) {
		if (client.level == null || client.player == null || activeMerchant == null) return;
		Map<String,Object> extra = new LinkedHashMap<>();
		extra.put("screen", Map.of("kind", "merchant", "completed_trades", completedTrades, "offer_count", offerCount));
		MerchantContext context = MERCHANT_CONTEXT.get(activeMerchant.getId());
		if (context != null) extra.putAll(context.asMap());
		emit(client, kind, activeMerchant, client.player,
			context == null ? EventReliability.CLIENT_INFERRED : EventReliability.SERVER_REQUIRED, extra, null);
	}

	private static void receiveContext(EefMerchantContextPayload payload, Minecraft client) {
		if (client.level == null) return;
		Entity merchant = client.level.getEntity(payload.merchantId());
		Entity playerEntity = client.level.getEntity(payload.playerId());
		Player player = playerEntity instanceof Player value ? value : client.player;
		if (merchant == null || player == null) return;
		MerchantContext merchantContext = new MerchantContext(payload.reputation(), reputationTier(payload.reputation()),
			payload.raidActive(), payload.offerCount(), payload.canTrade(), payload.refusalReason(), payload.gameTick());
		MERCHANT_CONTEXT.put(payload.merchantId(), merchantContext);
		emit(client, EntityEventKind.TRADE_ATTEMPTED, merchant, player, EventReliability.SERVER_REQUIRED,
			merchantContext.asMap(), null);
	}

	static String reputationTier(int reputation) {
		if (reputation >= 75) return "extremely_high";
		if (reputation >= 25) return "high";
		if (reputation >= -75) return "neutral";
		if (reputation >= -225) return "low";
		return "extremely_low";
	}
	private static void receive(EefTradePayload payload, Minecraft client) {
		if (client.level == null) return;
		Entity merchant = client.level.getEntity(payload.merchantId());
		Entity playerEntity = client.level.getEntity(payload.playerId());
		Player player = playerEntity instanceof Player value ? value : client.player;
		if (merchant == null || player == null) return;
		if (open && activeMerchant == null) activeMerchant = merchant;
		if (open && activeMerchant != null && activeMerchant.getId() == merchant.getId()) completedTrades++;
		Map<String,Object> trade = new LinkedHashMap<>();
		trade.put("cost_a", item(payload.costA(), payload.costACount()));
		trade.put("cost_b", item(payload.costB(), payload.costBCount()));
		trade.put("result", item(payload.result(), payload.resultCount()));
		trade.put("uses_before", payload.usesBefore()); trade.put("uses_after", payload.usesBefore() + 1);
		trade.put("max_uses", payload.maxUses()); trade.put("merchant_xp", payload.merchantXp());
		trade.put("session_count", completedTrades); trade.put("server_tick", payload.gameTick());
		Identifier detail; try { detail = Identifier.parse(payload.result()); } catch (RuntimeException ignored) { detail = null; }
		emit(client, EntityEventKind.TRADE_COMPLETED, merchant, player, EventReliability.SERVER_REQUIRED,
			Map.of("trade", Collections.unmodifiableMap(trade)), detail);
	}
	private static Map<String,Object> item(String type, int count) { return Map.of("type", type, "count", count, "empty", count <= 0 || type.equals("minecraft:air")); }
	private static void emit(Minecraft client, EntityEventKind kind, Entity merchant, Player player,
		EventReliability reliability, Map<String,Object> extra, Identifier detail) {
		Map<String,Object> context = new LinkedHashMap<>(extra);
		context.put("self", EefClientContext.entity(merchant)); context.put("merchant", EefClientContext.entity(merchant));
		context.put("actor", EefClientContext.entity(player)); context.put("target", EefClientContext.entity(player));
		context.put("observer", EefClientContext.entity(client.player));
		context.put("distance", Map.of("observer_to_self", client.player.distanceTo(merchant)));
		EntityEventBus.emit(new EntityEvent(client.level.getGameTime(), kind, merchant, player,
			merchant.blockPosition(), detail, reliability, context));
	}

	private record MerchantContext(int reputation, String tier, boolean raidActive, int offerCount,
		boolean canTrade, String refusalReason, long serverTick) {
		Map<String,Object> asMap() {
			return Map.of(
				"reputation", Map.of("score", reputation, "tier", tier),
				"trade_attempt", Map.of("raid_active", raidActive, "offer_count", offerCount,
					"can_trade", canTrade, "refusal_reason", refusalReason, "server_tick", serverTick));
		}
	}
}
