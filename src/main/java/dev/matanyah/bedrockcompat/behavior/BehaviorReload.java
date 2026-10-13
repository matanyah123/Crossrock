package dev.matanyah.bedrockcompat.behavior;

import net.fabricmc.fabric.api.resource.*;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Validation participates in vanilla's reload transaction; failed preparation keeps the old profile. */
public final class BehaviorReload implements SimpleSynchronousResourceReloadListener {
    private static final Map<ResourceManager,BehaviorProfile> prepared=Collections.synchronizedMap(new WeakHashMap<>());
    @Override public Identifier getFabricId(){return Identifier.fromNamespaceAndPath("bedrock_compat","behavior_profile");}
    @Override public void onResourceManagerReload(ResourceManager manager){prepared.put(manager,BehaviorProfile.compile(manager));}
    public static BehaviorProfile profile(ResourceManager manager){var p=prepared.get(manager);return p!=null?p:BehaviorProfile.compile(manager);}
    public static void register(){ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new BehaviorReload());}
}
