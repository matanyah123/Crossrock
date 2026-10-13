package dev.matanyah.bedrockcompat.behavior;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import java.util.*;

/** Extension contract for complete API subsystems. Preparation must be side-effect free. */
public interface BehaviorSubsystem {
    Identifier identifier();
    Object prepare(BehaviorDefinitions definitions,List<BehaviorPackResources.Content> packs);
    Session open(MinecraftServer server,Object plan,BehaviorStateStore persistentState);
    interface Session extends AutoCloseable {
        default void tick() {}
        @Override void close();
    }
    record Prepared(BehaviorSubsystem subsystem,Object plan) {}
    final class Registry {
        private static final Map<Identifier,BehaviorSubsystem> systems=new LinkedHashMap<>();
        private Registry() {}
        /** Register during common initialization, before any resource reload. */
        public static synchronized void register(BehaviorSubsystem subsystem) {
            if(systems.putIfAbsent(subsystem.identifier(),subsystem)!=null)throw new IllegalArgumentException("Duplicate behavior subsystem "+subsystem.identifier());
        }
        public static synchronized List<Prepared> prepare(BehaviorDefinitions definitions,List<BehaviorPackResources.Content> packs) {
            return systems.values().stream().map(s->new Prepared(s,s.prepare(definitions,packs))).toList();
        }
    }
}
