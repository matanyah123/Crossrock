package dev.matanyah.bedrockcompat.entity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-owned Bedrock pose. Kept out of vanilla entity-data IDs for unmodded clients. */
public record ArmorStandPosePayload(int entityId, java.util.UUID uuid, boolean active, int poseIndex) implements CustomPacketPayload {
    public static final Type<ArmorStandPosePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bedrock_compat", "armor_stand_pose"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ArmorStandPosePayload> CODEC = StreamCodec.ofMember(ArmorStandPosePayload::write,ArmorStandPosePayload::read);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    private void write(RegistryFriendlyByteBuf buffer){buffer.writeVarInt(entityId);buffer.writeUUID(uuid);buffer.writeBoolean(active);buffer.writeVarInt(poseIndex);}
    private static ArmorStandPosePayload read(RegistryFriendlyByteBuf buffer){return new ArmorStandPosePayload(buffer.readVarInt(),buffer.readUUID(),buffer.readBoolean(),Math.floorMod(buffer.readVarInt(),BedrockArmorStandState.POSE_COUNT));}
}
