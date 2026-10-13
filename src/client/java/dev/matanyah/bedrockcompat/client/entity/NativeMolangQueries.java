package dev.matanyah.bedrockcompat.client.entity;

import dev.matanyah.bedrockcompat.bedrock.entity.MolangQueryProvider;
import net.minecraft.world.entity.Entity;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Integration boundary for externally synchronized actor properties/data.
 * Factories run during extraction and must return immutable frame observations. No registration
 * grants permission to execute BP gameplay on the client. Later registrations take precedence.
 */
public final class NativeMolangQueries {
    private static final List<Function<Entity,MolangQueryProvider>> factories=new CopyOnWriteArrayList<>();
    private NativeMolangQueries() {}
    public static AutoCloseable register(Function<Entity,MolangQueryProvider> factory) {
        Objects.requireNonNull(factory);factories.add(factory);
        return ()->factories.remove(factory);
    }
    static MolangQueryProvider snapshot(Entity entity) {
        var snapshots=factories.stream().map(factory->Objects.requireNonNull(factory.apply(entity))).toList();
        return (name,args)-> {
            for(int i=snapshots.size()-1;i>=0;i--) {
                var value=snapshots.get(i).query(name,args);
                if(value.isPresent())return value;
            }
            return Optional.empty();
        };
    }
}
