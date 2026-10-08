package dev.matanyah.eef;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Client resource-pack rule names made available to the server's Brigadier suggestion provider. */
public record EefCommandCatalogPayload(List<Entry> rules) implements CustomPacketPayload {
	public static final Type<EefCommandCatalogPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(EntityEventFeatures.MOD_ID, "command_catalog"));
	public static final StreamCodec<RegistryFriendlyByteBuf,EefCommandCatalogPayload> CODEC = StreamCodec.ofMember(EefCommandCatalogPayload::write, EefCommandCatalogPayload::read);
	public record Entry(String id, int variants) {}
	public EefCommandCatalogPayload { rules = List.copyOf(rules); }
	public static void register() { PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC); }
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(Math.min(rules.size(), 2048));
		for (Entry entry : rules.stream().limit(2048).toList()) {
			buffer.writeUtf(entry.id(), 512); buffer.writeVarInt(Math.max(0, entry.variants()));
		}
	}
	private static EefCommandCatalogPayload read(RegistryFriendlyByteBuf buffer) {
		int encodedSize = Math.max(0, buffer.readVarInt());
		List<Entry> rules = new ArrayList<>(Math.min(encodedSize, 2048));
		for (int index = 0; index < encodedSize; index++) {
			String id = buffer.readUtf(512); int variants = Math.max(0, buffer.readVarInt());
			if (index < 2048) rules.add(new Entry(id, variants));
		}
		return new EefCommandCatalogPayload(rules);
	}
}
