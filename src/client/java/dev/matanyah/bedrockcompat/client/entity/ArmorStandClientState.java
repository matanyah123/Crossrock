package dev.matanyah.bedrockcompat.client.entity;

import dev.matanyah.bedrockcompat.entity.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import java.util.*;

/** Spawn and custom-payload ordering can differ; retain only a bounded short-lived pending queue. */
public final class ArmorStandClientState {
    private record Pending(ArmorStandPosePayload payload,int ticks) {}
    private static final Map<UUID,Pending> pending=new LinkedHashMap<>();
    private static net.minecraft.server.MinecraftServer hostServer;
    private static NativeEntityResources.Snapshot hostResources;
    private static Object reportedConnection;
    private static Boolean reportedPreference;
    private ArmorStandClientState() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ArmorStandPosePayload.TYPE,(payload,context)-> {
            if(!apply(payload)) {
                if(pending.size()>=1024)pending.remove(pending.keySet().iterator().next());
                pending.put(payload.uuid(),new Pending(payload,100));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client-> {
            var server=client.getSingleplayerServer();
            var resources=NativeEntityResources.snapshot();
            if(server==null) { hostServer=null;hostResources=null; }
            else if(server!=hostServer || resources!=hostResources) {
                hostServer=server;hostResources=resources;
                boolean enabled=NativeEntityResources.affectsArmorStand();
                server.execute(()->ArmorStandCompatibility.setEnabled(server,enabled));
            }
            if(client.player!=null&&ClientPlayNetworking.canSend(ArmorStandPackPayload.TYPE)) {
                boolean enabled=NativeEntityResources.affectsArmorStand();
                var connection=client.getConnection();
                if(connection!=reportedConnection||reportedPreference==null||reportedPreference!=enabled) {
                    reportedConnection=connection;reportedPreference=enabled;
                    ClientPlayNetworking.send(new ArmorStandPackPayload(enabled));
                }
            } else if(client.player!=null&&server==null&&NativeEntityResources.affectsArmorStand()) {
                NativeEntityResources.runtimeError("Armor stands: this server does not support Bedrock armor-stand synchronization; install Crossrock on the server for arms and pose interactions.");
            }
            var iterator=pending.entrySet().iterator();
            while(iterator.hasNext()) {
                var entry=iterator.next();var value=entry.getValue();
                if(value.ticks<=0||apply(value.payload))iterator.remove();
                else entry.setValue(new Pending(value.payload,value.ticks-1));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{pending.clear();reportedConnection=null;reportedPreference=null;});
    }
    private static boolean apply(ArmorStandPosePayload payload) {
        var level=Minecraft.getInstance().level;if(level==null)return false;
        if(!(level.getEntity(payload.entityId()) instanceof ArmorStand stand)||!stand.getUUID().equals(payload.uuid()))return false;
        ((BedrockArmorStandAccess)stand).bedrockCompatState().restore(payload.active(),payload.poseIndex(),stand.showArms());
        return true;
    }
}
