package dev.matanyah.bedrockcompat.client.entity;

import net.minecraft.client.player.AbstractClientPlayer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Optional integrations provide facts, not model choices. Pack scripts choose the visuals. */
public final class NativePlayerAnimationApi {
    @FunctionalInterface public interface InputProvider {
        /** Runs on the client thread during extraction. Supply value-only Molang data, never game objects. */
        void collect(AbstractClientPlayer player,float partialTick,NativeEntityRenderer.View view,
                     Map<String,Object> variables,Map<String,Object> queries);
    }
    private static final List<InputProvider> providers=new CopyOnWriteArrayList<>();
    private NativePlayerAnimationApi() {}
    public static AutoCloseable register(InputProvider provider) {
        Objects.requireNonNull(provider);providers.add(provider);return ()->providers.remove(provider);
    }
    static void collect(AbstractClientPlayer player,float partial,NativeEntityRenderer.View view,
            Map<String,Object> variables,Map<String,Object> queries) {
        for(var provider:providers) {
            var nextVariables=new HashMap<>(variables);var nextQueries=new HashMap<>(queries);
            try {
                provider.collect(player,partial,view,nextVariables,nextQueries);
                nextVariables.replaceAll((key,value)->immutable(value));nextQueries.replaceAll((key,value)->immutable(value));
                variables.clear();variables.putAll(nextVariables);queries.clear();queries.putAll(nextQueries);
            }catch(RuntimeException failure){NativeEntityResources.runtimeError("Player animation input provider: "+failure.getMessage());}
        }
    }
    private static Object immutable(Object value) {
        if(value instanceof String||value instanceof Boolean)return value;
        if(value instanceof Number n&&Double.isFinite(n.doubleValue()))return n.doubleValue();
        if(value instanceof List<?> list)return list.stream().map(NativePlayerAnimationApi::immutable).toList();
        if(value instanceof Set<?> set){Set<Object> copy=new HashSet<>();for(Object v:set)copy.add(immutable(v));return Set.copyOf(copy);}
        if(value instanceof Map<?,?> map){Map<String,Object> copy=new HashMap<>();map.forEach((k,v)->copy.put(k.toString(),immutable(v)));return Map.copyOf(copy);}
        throw new IllegalArgumentException("Player inputs must be finite, value-only Molang data");
    }
}
