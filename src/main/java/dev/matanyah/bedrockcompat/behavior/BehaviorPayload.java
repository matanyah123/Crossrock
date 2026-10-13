package dev.matanyah.bedrockcompat.behavior;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

/** Bounded versioned envelope. Client input never contains a filesystem path or executable callback. */
public record BehaviorPayload(int version,UUID session,long revision,String kind,String metadata,byte[] bytes) implements CustomPacketPayload {
    public static final int VERSION=1,CHUNK=32*1024;
    public static final Type<BehaviorPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("bedrock_compat","behavior_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BehaviorPayload> CODEC=StreamCodec.ofMember(BehaviorPayload::write,BehaviorPayload::read);
    public BehaviorPayload{if(bytes.length>CHUNK)throw new IllegalArgumentException("Oversized behavior packet");bytes=bytes.clone();}
    @Override public byte[] bytes(){return bytes.clone();}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    private void write(RegistryFriendlyByteBuf b){b.writeVarInt(version);b.writeUUID(session);b.writeLong(revision);b.writeUtf(kind,32);b.writeUtf(metadata,16384);b.writeByteArray(bytes);}
    private static BehaviorPayload read(RegistryFriendlyByteBuf b){return new BehaviorPayload(b.readVarInt(),b.readUUID(),b.readLong(),b.readUtf(32),b.readUtf(16384),b.readByteArray(CHUNK));}
}
