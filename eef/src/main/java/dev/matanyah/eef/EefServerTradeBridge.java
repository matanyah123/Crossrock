package dev.matanyah.eef;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.LinkedHashSet;

/** Emits completed transactions without encoding any pack- or entity-specific behavior. */
public final class EefServerTradeBridge {
	private EefServerTradeBridge() {}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && target instanceof AbstractVillager merchant)
				captureAttempt(merchant, serverPlayer);
			return net.minecraft.world.InteractionResult.PASS;
		});
	}

	private static void captureAttempt(AbstractVillager merchant, ServerPlayer player) {
		int reputation = merchant instanceof Villager villager ? villager.getPlayerReputation(player) : 0;
		boolean raid = merchant.level() instanceof ServerLevel serverLevel && serverLevel.isRaided(merchant.blockPosition());
		int offers = merchant.getOffers().size();
		String refusal = refusalReason(merchant, reputation, raid, offers);
		EefMerchantContextPayload payload = new EefMerchantContextPayload(merchant.level().getGameTime(),
			merchant.getId(), player.getId(), reputation, raid, offers, refusal.equals("none"), refusal);
		LinkedHashSet<ServerPlayer> recipients = new LinkedHashSet<>(PlayerLookup.tracking(merchant));
		recipients.add(player);
		for (ServerPlayer recipient : recipients) if (ServerPlayNetworking.canSend(recipient, EefMerchantContextPayload.TYPE))
			ServerPlayNetworking.send(recipient, payload);
	}

	private static String refusalReason(AbstractVillager merchant, int reputation, boolean raid, int offers) {
		if (merchant instanceof LivingEntity living && living.isSleeping()) return "sleeping";
		if (raid) return "raid";
		if (merchant instanceof Villager villager) {
			String profession = villager.getVillagerData().profession().unwrapKey()
				.map(key -> key.identifier().toString()).orElse("unknown");
			if (profession.equals("minecraft:nitwit")) return "nitwit";
			if (profession.equals("minecraft:none")) return "unemployed";
			if (reputation < -225) return "low_reputation";
		}
		return offers == 0 ? "no_offers" : "none";
	}

	public static void capture(Merchant merchant, Player player, MerchantOffer offer, ItemStack result) {
		if (player.level().isClientSide() || !(merchant instanceof Entity entity)) return;
		ItemStack a = offer.getCostA(), b = offer.getCostB();
		EefTradePayload payload = new EefTradePayload(entity.level().getGameTime(), entity.getId(), type(entity),
			entity.getX(), entity.getY(), entity.getZ(), player.getId(), item(a), a.getCount(), item(b), b.getCount(),
			item(result), result.getCount(), offer.getUses(), offer.getMaxUses(), merchant.getVillagerXp());
		LinkedHashSet<ServerPlayer> recipients = new LinkedHashSet<>(PlayerLookup.tracking(entity));
		if (player instanceof ServerPlayer serverPlayer) recipients.add(serverPlayer);
		for (ServerPlayer recipient : recipients) if (ServerPlayNetworking.canSend(recipient, EefTradePayload.TYPE))
			ServerPlayNetworking.send(recipient, payload);
	}
	private static String item(ItemStack stack) { return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
	private static String type(Entity entity) { return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(); }
}
