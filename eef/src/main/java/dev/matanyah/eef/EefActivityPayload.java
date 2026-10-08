package dev.matanyah.eef;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generic server-observed brain, behavior, and navigation state for a tracked entity. */
public record EefActivityPayload(long gameTick, int entityId, String primaryActivity,
	List<String> activities, List<String> behaviors, boolean navigating, boolean stuck,
	Map<String,Integer> inventory, int observerReputation, Map<String,String> memoryDimensions,
	Map<String,Double> memoryDistances)
	implements CustomPacketPayload {
	public static final Type<EefActivityPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "entity_activity"));
	public static final StreamCodec<RegistryFriendlyByteBuf,EefActivityPayload> CODEC = StreamCodec.ofMember(EefActivityPayload::write, EefActivityPayload::read);

	public EefActivityPayload {
		activities = List.copyOf(activities); behaviors = List.copyOf(behaviors); inventory = Map.copyOf(inventory);
		memoryDimensions = Map.copyOf(memoryDimensions); memoryDistances = Map.copyOf(memoryDistances);
	}
	public static void register() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeLong(gameTick); buffer.writeVarInt(entityId); buffer.writeUtf(primaryActivity, 128);
		writeStrings(buffer, activities); writeStrings(buffer, behaviors);
		buffer.writeBoolean(navigating); buffer.writeBoolean(stuck);
		buffer.writeVarInt(inventory.size());
		inventory.forEach((type, count) -> { buffer.writeUtf(type, 256); buffer.writeVarInt(count); });
		buffer.writeInt(observerReputation);
		buffer.writeVarInt(memoryDimensions.size());
		memoryDimensions.forEach((name, dimension) -> { buffer.writeUtf(name, 64); buffer.writeUtf(dimension, 256); });
		buffer.writeVarInt(memoryDistances.size());
		memoryDistances.forEach((name, distance) -> { buffer.writeUtf(name, 64); buffer.writeDouble(distance); });
	}
	private static EefActivityPayload read(RegistryFriendlyByteBuf buffer) {
		long gameTick = buffer.readLong(); int entityId = buffer.readVarInt(); String primary = buffer.readUtf(128);
		List<String> activities = readStrings(buffer), behaviors = readStrings(buffer);
		boolean navigating = buffer.readBoolean(), stuck = buffer.readBoolean();
		int size = Math.min(buffer.readVarInt(), 256); Map<String,Integer> inventory = new LinkedHashMap<>();
		for (int i = 0; i < size; i++) inventory.put(buffer.readUtf(256), buffer.readVarInt());
		int reputation = buffer.readInt();
		size = Math.min(buffer.readVarInt(), 16); Map<String,String> dimensions = new LinkedHashMap<>();
		for (int i = 0; i < size; i++) dimensions.put(buffer.readUtf(64), buffer.readUtf(256));
		size = Math.min(buffer.readVarInt(), 16); Map<String,Double> distances = new LinkedHashMap<>();
		for (int i = 0; i < size; i++) distances.put(buffer.readUtf(64), buffer.readDouble());
		return new EefActivityPayload(gameTick, entityId, primary, activities, behaviors, navigating, stuck,
			inventory, reputation, dimensions, distances);
	}
	private static void writeStrings(RegistryFriendlyByteBuf buffer, List<String> values) {
		buffer.writeVarInt(values.size()); for (String value : values) buffer.writeUtf(value, 256);
	}
	private static List<String> readStrings(RegistryFriendlyByteBuf buffer) {
		int size = Math.min(buffer.readVarInt(), 256); List<String> values = new ArrayList<>(size);
		for (int i = 0; i < size; i++) values.add(buffer.readUtf(256));
		return List.copyOf(values);
	}
}
