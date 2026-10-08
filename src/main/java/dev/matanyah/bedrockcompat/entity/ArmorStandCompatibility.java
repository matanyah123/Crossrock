package dev.matanyah.bedrockcompat.entity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import dev.matanyah.bedrockcompat.BedrockCompat;

/** Server-thread authority. Integrated hosts supply their resolved resource-pack policy. */
public final class ArmorStandCompatibility {
    private static final Map<MinecraftServer,Boolean> policies = new WeakHashMap<>();
    private static final Map<MinecraftServer,Set<UUID>> preferences=new WeakHashMap<>();
    private static final Map<MinecraftServer,String> modes=new WeakHashMap<>();
    private static final Map<MinecraftServer,Integer> pendingPolicy=new WeakHashMap<>();
    private ArmorStandCompatibility() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server-> {
            String mode="auto";
            var file=FabricLoader.getInstance().getConfigDir().resolve("bedrock_compat-server.json");
            if(Files.isRegularFile(file))try(var reader=Files.newBufferedReader(file)) {
                var config=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                if(config.has("armor_stands"))mode=config.get("armor_stands").getAsString();
                if(!Set.of("auto","enabled","disabled").contains(mode))throw new IllegalArgumentException("Expected auto, enabled or disabled");
            } catch(Exception exception){BedrockCompat.LOGGER.error("Invalid armor-stand server config; disabling automatic changes",exception);mode="disabled";}
            modes.put(server,mode);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server-> {
            if(server.isDedicatedServer())setEnabled(server,"enabled".equals(modes.get(server)));
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity,level)-> {
            if (!(entity instanceof ArmorStand stand)) return;
            reconcile(stand);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server-> {
            Integer due=pendingPolicy.get(server);
            if(due!=null&&server.getTickCount()>=due) {
                pendingPolicy.remove(server);
                setEnabled(server,!preferences.getOrDefault(server,Set.of()).isEmpty());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{policies.remove(server);preferences.remove(server);modes.remove(server);pendingPolicy.remove(server);});
    }

    /** Dedicated servers explicitly choose whether clients may request this world-wide gameplay mode.
     * Auto enables it while at least one connected modded client has a winning Bedrock armor-stand pack.
     * The server remains authoritative for pose cycling, inventory and persistence. */
    public static void clientPreference(MinecraftServer server,UUID player,boolean enabled) {
        if(!server.isDedicatedServer())return; // The integrated host's resolved stack owns its policy.
        var players=preferences.computeIfAbsent(server,key->new HashSet<>());
        boolean changed=enabled?players.add(player):players.remove(player);
        // Coalesce requests and cap global entity walks to once per second even if a peer
        // repeatedly toggles its selection. A later request cannot postpone an existing update.
        if(changed)pendingPolicy.putIfAbsent(server,server.getTickCount()+20);
    }

    /** Also used after /data and entity deserialization; a client's payload never sets policy. */
    public static void reconcile(ArmorStand stand) {
        if(stand.level().isClientSide())return;
        Boolean enabled=policies.get(stand.level().getServer());
        if(enabled!=null)apply(stand,enabled);
        else if(((BedrockArmorStandAccess)stand).bedrockCompatState().active())stand.setShowArms(true);
    }

    /** Invoke on the server executor; also updates loaded stands after a pack reload. */
    public static void setEnabled(MinecraftServer server,boolean enabled) {
        if(!server.isSameThread())throw new IllegalStateException("Armor-stand policy must run on the server thread");
        String mode=modes.getOrDefault(server,"auto");
        if(mode.equals("enabled"))enabled=true;else if(mode.equals("disabled"))enabled=false;
        Boolean previous=policies.put(server,enabled);
        if(previous!=null && previous==enabled)return;
        for(var level:server.getAllLevels())for(Entity entity:level.getAllEntities())
            if(entity instanceof ArmorStand stand)apply(stand,enabled);
    }

    private static void apply(ArmorStand stand,boolean enabled) {
        var state=((BedrockArmorStandAccess)stand).bedrockCompatState();
        if(enabled) {
            state.activate(stand.showArms());
            stand.setShowArms(true);
        } else if(state.active()) {
            stand.setShowArms(state.originalArms());
            state.deactivate();
        } else return;
        ArmorStandNetworking.changed(stand);
    }
}
