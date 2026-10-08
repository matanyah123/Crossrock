package dev.matanyah.eef;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Server-resolved command request interpreted against the receiving client's active EEF pack. */
public record EefCommandPayload(String operation, List<Integer> entityIds, String rule, int variant, int page)
	implements CustomPacketPayload {
	public static final Type<EefCommandPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "command"));
	public static final StreamCodec<RegistryFriendlyByteBuf,EefCommandPayload> CODEC = StreamCodec.ofMember(EefCommandPayload::write, EefCommandPayload::read);
	public EefCommandPayload { entityIds = List.copyOf(entityIds); rule = rule == null ? "" : rule; }
	public static void register() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUtf(operation, 16); buffer.writeVarInt(entityIds.size());
		for (int id : entityIds) buffer.writeVarInt(id);
		buffer.writeUtf(rule, 512); buffer.writeVarInt(variant); buffer.writeVarInt(page);
	}
	private static EefCommandPayload read(RegistryFriendlyByteBuf buffer) {
		String operation = buffer.readUtf(16); int encodedSize = Math.max(0, buffer.readVarInt());
		List<Integer> ids = new ArrayList<>(Math.min(encodedSize, 256));
		for (int i = 0; i < encodedSize; i++) { int id = buffer.readVarInt(); if (i < 256) ids.add(id); }
		return new EefCommandPayload(operation, ids, buffer.readUtf(512), buffer.readVarInt(), buffer.readVarInt());
	}
}
