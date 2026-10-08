package dev.matanyah.bedrockcompat.entity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Reports one capability preference, not entity IDs, poses or arbitrary NBT. */
public record ArmorStandPackPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<ArmorStandPackPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("bedrock_compat","armor_stand_pack"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ArmorStandPackPayload> CODEC=StreamCodec.of(
        (buffer,payload)->buffer.writeBoolean(payload.enabled),buffer->new ArmorStandPackPayload(buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
