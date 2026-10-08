package dev.matanyah.eef;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Compact authoritative server-to-client damage/death envelope. */
public record EefDamagePayload(
	boolean death, long gameTick,
	int victimId, String victimType, double x, double y, double z,
	float amount, float baseAmount, boolean blocked,
	String damageType, String messageId, boolean direct,
	int attackerId, String attackerType,
	int directEntityId, String directEntityType,
	String weaponType
) implements CustomPacketPayload {
	public static final Type<EefDamagePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "authoritative_damage"));
	public static final StreamCodec<RegistryFriendlyByteBuf,EefDamagePayload> CODEC = StreamCodec.ofMember(EefDamagePayload::write, EefDamagePayload::read);

	public static void register() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeBoolean(death); buffer.writeLong(gameTick);
		buffer.writeVarInt(victimId); buffer.writeUtf(victimType); buffer.writeDouble(x); buffer.writeDouble(y); buffer.writeDouble(z);
		buffer.writeFloat(amount); buffer.writeFloat(baseAmount); buffer.writeBoolean(blocked);
		buffer.writeUtf(damageType); buffer.writeUtf(messageId); buffer.writeBoolean(direct);
		buffer.writeVarInt(attackerId + 1); buffer.writeUtf(attackerType);
		buffer.writeVarInt(directEntityId + 1); buffer.writeUtf(directEntityType); buffer.writeUtf(weaponType);
	}

	private static EefDamagePayload read(RegistryFriendlyByteBuf buffer) {
		return new EefDamagePayload(buffer.readBoolean(), buffer.readLong(),
			buffer.readVarInt(), buffer.readUtf(256), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
			buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(),
			buffer.readUtf(256), buffer.readUtf(256), buffer.readBoolean(),
			buffer.readVarInt() - 1, buffer.readUtf(256), buffer.readVarInt() - 1, buffer.readUtf(256), buffer.readUtf(256));
	}
}
