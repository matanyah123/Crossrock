package dev.matanyah.eef;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-authoritative context for an attempt to interact with a merchant. */
public record EefMerchantContextPayload(long gameTick, int merchantId, int playerId,
	int reputation, boolean raidActive, int offerCount, boolean canTrade, String refusalReason)
	implements CustomPacketPayload {
	public static final Type<EefMerchantContextPayload> TYPE = new Type<>(
		Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "merchant_context"));
	public static final StreamCodec<RegistryFriendlyByteBuf,EefMerchantContextPayload> CODEC =
		StreamCodec.ofMember(EefMerchantContextPayload::write, EefMerchantContextPayload::read);

	public static void register() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeLong(gameTick); buffer.writeVarInt(merchantId); buffer.writeVarInt(playerId);
		buffer.writeVarInt(reputation); buffer.writeBoolean(raidActive); buffer.writeVarInt(offerCount);
		buffer.writeBoolean(canTrade); buffer.writeUtf(refusalReason, 64);
	}

	private static EefMerchantContextPayload read(RegistryFriendlyByteBuf buffer) {
		return new EefMerchantContextPayload(buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(),
			buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(64));
	}
}
