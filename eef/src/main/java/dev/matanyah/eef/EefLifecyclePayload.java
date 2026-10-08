package dev.matanyah.eef;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Generic authoritative entity lifecycle/action envelope. */
public record EefLifecyclePayload(String event, long gameTick, int entityId, String entityType,
	int sourceId, String sourceType, String cause, String itemType, boolean itemEnchanted)
	implements CustomPacketPayload {
	public static final Type<EefLifecyclePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "lifecycle"));
	public static final StreamCodec<RegistryFriendlyByteBuf,EefLifecyclePayload> CODEC = StreamCodec.ofMember(EefLifecyclePayload::write, EefLifecyclePayload::read);
	public static void register() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUtf(event, 64); buffer.writeLong(gameTick); buffer.writeVarInt(entityId); buffer.writeUtf(entityType, 256);
		buffer.writeVarInt(sourceId + 1); buffer.writeUtf(sourceType, 256); buffer.writeUtf(cause, 128);
		buffer.writeUtf(itemType, 256); buffer.writeBoolean(itemEnchanted);
	}
	private static EefLifecyclePayload read(RegistryFriendlyByteBuf buffer) {
		return new EefLifecyclePayload(buffer.readUtf(64), buffer.readLong(), buffer.readVarInt(), buffer.readUtf(256),
			buffer.readVarInt() - 1, buffer.readUtf(256), buffer.readUtf(128), buffer.readUtf(256), buffer.readBoolean());
	}
}
