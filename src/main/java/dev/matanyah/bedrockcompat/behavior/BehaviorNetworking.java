package dev.matanyah.bedrockcompat.behavior;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import com.google.gson.*;
import java.util.*;

public final class BehaviorNetworking {
    private record Transfer(ServerPlayer player,UUID session,long revision,byte[] data,int offset,long deadline){}
    private static final class Connections {
        final Map<UUID,Transfer> transfers=new HashMap<>();
        final Map<UUID,UUID> accepted=new HashMap<>();
        final Map<UUID,Long> deadlines=new HashMap<>();
    }
    private static final Map<MinecraftServer,Connections> worlds=Collections.synchronizedMap(new IdentityHashMap<>());
    private static Connections state(MinecraftServer server){return worlds.computeIfAbsent(server,k->new Connections());}
    public static void register(){
        PayloadTypeRegistry.clientboundPlay().register(BehaviorPayload.TYPE,BehaviorPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BehaviorPayload.TYPE,BehaviorPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->offer(server,handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->cleanup(server,handler.player.getUUID()));
        ServerPlayNetworking.registerGlobalReceiver(BehaviorPayload.TYPE,(p,c)->receive(c.server(),c.player(),p));
    }
    private static void cleanup(MinecraftServer server,UUID id){var state=state(server);state.transfers.remove(id);state.accepted.remove(id);state.deadlines.remove(id);}
    public static void changed(MinecraftServer server){for(var p:server.getPlayerList().getPlayers())offer(server,p);}
    public static void stopped(MinecraftServer server){worlds.remove(server);}
    private static void offer(MinecraftServer server,ServerPlayer player){
        cleanup(server,player.getUUID());var state=state(server);var runtime=BehaviorRuntime.get(server);
        if(!ServerPlayNetworking.canSend(player,BehaviorPayload.TYPE)) {
            if(runtime.profile().active())player.connection.disconnect(Component.literal("This world requires Crossrock behavior protocol v1"));return;
        }
        JsonObject metadata=new JsonObject();metadata.addProperty("hash",runtime.profile().hash());metadata.addProperty("size",runtime.profile().bundle().length);JsonArray packs=new JsonArray();runtime.profile().packs().forEach(c->packs.add(c.pack().uuid()));metadata.add("packs",packs);
        send(player,runtime,"offer",metadata.toString(),new byte[0]);state.deadlines.put(player.getUUID(),(long)server.getTickCount()+2400);
    }
    private static void receive(MinecraftServer server,ServerPlayer player,BehaviorPayload p){
        var state=state(server);var r=BehaviorRuntime.get(server);if(p.version()!=BehaviorPayload.VERSION){player.connection.disconnect(Component.literal("Incompatible Crossrock protocol"));return;}
        if(!p.session().equals(r.session())||p.revision()!=r.revision())return;
        if(p.kind().equals("request")&&p.metadata().equals(r.profile().hash())&&!state.transfers.containsKey(player.getUUID())&&!state.accepted.containsKey(player.getUUID())) {
            state.transfers.put(player.getUUID(),new Transfer(player,r.session(),r.revision(),r.profile().bundle(),0,(long)server.getTickCount()+2400));
        }else if(p.kind().equals("ack")&&p.metadata().equals(r.profile().hash())){state.accepted.put(player.getUUID(),r.session());state.deadlines.remove(player.getUUID());state.transfers.remove(player.getUUID());}
        else if(p.kind().equals("reject"))player.connection.disconnect(Component.literal("Crossrock add-on negotiation failed: "+p.metadata()));
    }
    public static void tick(MinecraftServer server){
        var state=state(server);
        for(var player:List.copyOf(server.getPlayerList().getPlayers())) {
            if(state.deadlines.getOrDefault(player.getUUID(),Long.MAX_VALUE)<server.getTickCount()){cleanup(server,player.getUUID());player.connection.disconnect(Component.literal("Crossrock pack negotiation timed out"));continue;}
            var t=state.transfers.get(player.getUUID());if(t==null)continue;int offset=t.offset();
            for(int i=0;i<4&&offset<t.data().length;i++){int end=Math.min(offset+BehaviorPayload.CHUNK,t.data().length);ServerPlayNetworking.send(player,new BehaviorPayload(1,t.session(),t.revision(),"chunk",Integer.toString(offset),Arrays.copyOfRange(t.data(),offset,end)));offset=end;}
            if(offset==t.data().length)state.transfers.remove(player.getUUID());else state.transfers.put(player.getUUID(),new Transfer(player,t.session(),t.revision(),t.data(),offset,t.deadline()));
        }
    }
    public static boolean accepted(ServerPlayer player){var server=player.level().getServer();return Objects.equals(state(server).accepted.get(player.getUUID()),BehaviorRuntime.get(server).session());}
    private static void send(ServerPlayer p,BehaviorRuntime r,String kind,String metadata,byte[] data){ServerPlayNetworking.send(p,new BehaviorPayload(1,r.session(),r.revision(),kind,metadata,data));}
}
