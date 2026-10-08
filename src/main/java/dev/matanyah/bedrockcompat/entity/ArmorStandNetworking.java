package dev.matanyah.bedrockcompat.entity;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;

public final class ArmorStandNetworking {
    private ArmorStandNetworking() {}
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(ArmorStandPosePayload.TYPE,ArmorStandPosePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ArmorStandPackPayload.TYPE,ArmorStandPackPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ArmorStandPackPayload.TYPE,(payload,context)->
            ArmorStandCompatibility.clientPreference(context.server(),context.player().getUUID(),payload.enabled()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->
            ArmorStandCompatibility.clientPreference(server,handler.player.getUUID(),false));
        EntityTrackingEvents.START_TRACKING.register((entity,player)-> {
            if(entity instanceof ArmorStand stand)send(stand,player);
        });
    }
    public static void changed(ArmorStand stand) {
        for(ServerPlayer player:PlayerLookup.tracking(stand))send(stand,player);
    }
    private static void send(ArmorStand stand,ServerPlayer player) {
        if(!ServerPlayNetworking.canSend(player,ArmorStandPosePayload.TYPE))return;
        BedrockArmorStandState state=((BedrockArmorStandAccess)stand).bedrockCompatState();
        ServerPlayNetworking.send(player,new ArmorStandPosePayload(stand.getId(),stand.getUUID(),state.active(),state.poseIndex()));
    }
}
