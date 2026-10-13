package dev.matanyah.bedrockcompat.behavior;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import java.util.*;

/** Server-thread owner. A successful resource transaction replaces one immutable generation. */
public final class BehaviorRuntime {
    private static final Map<MinecraftServer,BehaviorRuntime> worlds=Collections.synchronizedMap(new IdentityHashMap<>());
    private final MinecraftServer server;private final BehaviorStateStore store;
    private List<BehaviorSubsystem.Session> systems=List.of();
    private BehaviorProfile profile=BehaviorProfile.EMPTY;private long revision;private UUID session=UUID.randomUUID();
    private BehaviorRuntime(MinecraftServer server){this.server=server;try{store=new BehaviorStateStore(server.getWorldPath(LevelResource.ROOT).resolve("data/crossrock/state.json"));}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
    public static BehaviorRuntime get(MinecraftServer server){return worlds.computeIfAbsent(server,BehaviorRuntime::new);}
    public BehaviorProfile profile(){return profile;} public long revision(){return revision;} public UUID session(){return session;}public BehaviorStateStore store(){return store;}
    public void activate(){
        var next=BehaviorReload.profile(server.getResourceManager());
        List<BehaviorSubsystem.Session> opened=new ArrayList<>();
        try{for(var prepared:next.subsystems())opened.add(prepared.subsystem().open(server,prepared.plan(),store));}catch(RuntimeException failure){for(var openedSystem:opened)openedSystem.close();throw failure;}
        closeSystems();systems=List.copyOf(opened);profile=next;revision++;session=UUID.randomUUID();
        for(var level:server.getAllLevels())for(var entity:level.getAllEntities())if(entity instanceof BehaviorEntity actor)actor.reconcile(profile.definitions());
        BehaviorNetworking.changed(server);
        dev.matanyah.bedrockcompat.BedrockCompat.LOGGER.info("Behavior generation {}: {} packs, {} actors, {} items; {} deferred subsystem declarations",revision,profile.packs().size(),profile.definitions().registry("minecraft:entity").size(),profile.definitions().registry("minecraft:item").size(),profile.definitions().deferred().size());
    }
    private void closeSystems(){for(int i=systems.size()-1;i>=0;i--)systems.get(i).close();systems=List.of();}
    public void save(){try{store.save();}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
    public static void register(){
        BehaviorIdentities.register();BehaviorReload.register();BehaviorNetworking.register();BehaviorCommands.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server->get(server).activate());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,manager,success)->{if(success)get(server).activate();});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{get(server).save();get(server).closeSystems();});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{worlds.remove(server);BehaviorNetworking.stopped(server);});
        ServerTickEvents.END_SERVER_TICK.register(server->{BehaviorNetworking.tick(server);for(var system:get(server).systems)system.tick();if(server.getTickCount()%1200==0)get(server).save();});
    }
}
