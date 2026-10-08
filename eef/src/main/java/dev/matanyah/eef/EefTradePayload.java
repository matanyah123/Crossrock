package dev.matanyah.eef;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Authoritative, generic merchant transaction envelope. */
public record EefTradePayload(long gameTick, int merchantId, String merchantType,
	double x, double y, double z, int playerId,
	String costA, int costACount, String costB, int costBCount,
	String result, int resultCount, int usesBefore, int maxUses, int merchantXp)
	implements CustomPacketPayload {
	public static final Type<EefTradePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "trade_completed"));
	public static final StreamCodec<RegistryFriendlyByteBuf,EefTradePayload> CODEC = StreamCodec.ofMember(EefTradePayload::write, EefTradePayload::read);

	public static void register() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeLong(gameTick); buffer.writeVarInt(merchantId); buffer.writeUtf(merchantType, 256);
		buffer.writeDouble(x); buffer.writeDouble(y); buffer.writeDouble(z); buffer.writeVarInt(playerId);
		buffer.writeUtf(costA, 256); buffer.writeVarInt(costACount); buffer.writeUtf(costB, 256); buffer.writeVarInt(costBCount);
		buffer.writeUtf(result, 256); buffer.writeVarInt(resultCount); buffer.writeVarInt(usesBefore);
		buffer.writeVarInt(maxUses); buffer.writeVarInt(merchantXp);
	}
	private static EefTradePayload read(RegistryFriendlyByteBuf buffer) {
		return new EefTradePayload(buffer.readLong(), buffer.readVarInt(), buffer.readUtf(256),
			buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readVarInt(),
			buffer.readUtf(256), buffer.readVarInt(), buffer.readUtf(256), buffer.readVarInt(),
			buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
	}
}
