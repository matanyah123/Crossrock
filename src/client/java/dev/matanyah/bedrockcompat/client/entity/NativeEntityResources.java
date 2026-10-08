package dev.matanyah.bedrockcompat.client.entity;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.BedrockCompat;
import dev.matanyah.bedrockcompat.bedrock.entity.BedrockGeometry;
import dev.matanyah.bedrockcompat.bedrock.entity.BedrockRenderController;
import dev.matanyah.bedrockcompat.bedrock.entity.BedrockClientEntity;
import dev.matanyah.bedrockcompat.bedrock.entity.BedrockAnimation;
import dev.matanyah.bedrockcompat.bedrock.entity.BedrockAnimationController;
import dev.matanyah.bedrockcompat.bedrock.entity.EntityPackPriority;
import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackProvider;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Atomic resource-reload boundary for the native Bedrock entity runtime. */
public final class NativeEntityResources implements SimpleSynchronousResourceReloadListener {
    public record Snapshot(Map<String,String> definitions, Map<String,BedrockGeometry> geometries,
                           Map<String,BedrockRenderController> renderControllers,
                           Map<String,BedrockClientEntity> entities, Map<String,BedrockAnimation> animations,
                           Map<String,BedrockAnimationController> animationControllers, Set<Identifier> textures,
                           EntityPackPriority priority,dev.matanyah.bedrockcompat.bedrock.entity.BedrockMaterials materials,
                           Map<String,Map<String,Object>> packSettings,List<String> errors) {}
    private static volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of(),
        new EntityPackPriority(VirtualCompatibilityPackProvider.PACK_ID,Map.of(),Map.of()),new dev.matanyah.bedrockcompat.bedrock.entity.BedrockMaterials(Map.of()),Map.of(),List.of());
    private static final Set<String> runtimeErrors = Collections.synchronizedSet(new LinkedHashSet<>());
    public static Snapshot snapshot() { return snapshot; }
    public static Optional<JsonObject> definition(String id) {
        String json=snapshot.definitions().get(id);
        return json==null?Optional.empty():Optional.of(JsonParser.parseString(json).getAsJsonObject());
    }
    public static List<String> errors(){
        List<String> out=new ArrayList<>(snapshot.errors());
        synchronized(runtimeErrors){out.addAll(runtimeErrors);}return List.copyOf(out);
    }
    public static void runtimeError(String error) {
        synchronized(runtimeErrors) {
            if(runtimeErrors.size()<256 && runtimeErrors.add(error))BedrockCompat.LOGGER.warn("Native entity evaluation: {}",error);
        }
    }
    public static boolean hasPriority(String entity, Identifier texture) {
        return snapshot.priority.owns(entity,texture.toString());
    }
    public static boolean affectsArmorStand() {
        Snapshot current=snapshot;
        boolean touched=current.definitions.containsKey("minecraft:armor_stand")
            || current.definitions.containsKey("geometry.armor_stand")
            || current.textures.contains(Identifier.fromNamespaceAndPath("bedrock_native","textures/entity/armor_stand.png"));
        return touched && current.priority.owns("minecraft:armor_stand","minecraft:textures/entity/armorstand/armorstand.png");
    }
    public static void register(){ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new NativeEntityResources());}
    @Override public Identifier getFabricId(){return Identifier.fromNamespaceAndPath("bedrock_compat","native_entities");}
    @Override public void onResourceManagerReload(ResourceManager manager) {
        NativeEntityTextures.reset();
        Map<String,String> next=new LinkedHashMap<>();List<String> failures=new ArrayList<>();
        Map<String,Map<String,Object>> settings=new LinkedHashMap<>();
        runtimeErrors.clear();
        Map<String,Integer> ranks=new LinkedHashMap<>();
        manager.listPacks().forEach(pack->ranks.put(pack.packId(),ranks.size()));
        Map<String,String> javaSources=new HashMap<>();
        var loader=net.fabricmc.loader.api.FabricLoader.getInstance();
        boolean optifine=loader.isModLoaded("optifine")||loader.isModLoaded("optifabric");
        List<String> directories=new ArrayList<>(List.of("textures/entity"));
        if(optifine||loader.isModLoaded("entity_texture_features"))directories.addAll(List.of("optifine/random/entity","optifine/mob"));
        if(optifine||loader.isModLoaded("entity_model_features"))directories.addAll(List.of("optifine/cem","emf/cem"));
        for(String directory:directories)
            manager.listResources(directory,id->true).forEach((id,resource)->javaSources.put(id.toString(),resource.sourcePackId()));
        Set<Identifier> textures=Set.copyOf(manager.listResources("textures",id->id.getNamespace().equals("bedrock_native")&&id.getPath().endsWith(".png")).keySet());
        manager.listResources("entity_definitions",id->id.getNamespace().equals("bedrock_native")&&id.getPath().endsWith(".json"))
            .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
                try(var reader=new InputStreamReader(entry.getValue().open(),StandardCharsets.UTF_8)) {
                    JsonObject envelope=JsonParser.parseReader(reader).getAsJsonObject();
                    next.put(envelope.get("id").getAsString(),envelope.getAsJsonObject("definition").toString());
                    if(envelope.has("pack_settings")) {
                        Map<String,Object> values=new LinkedHashMap<>();
                        envelope.getAsJsonObject("pack_settings").entrySet().forEach(setting-> {
                            var value=setting.getValue();
                            if(value.isJsonArray())values.put(setting.getKey(),value.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList());
                            else if(value.isJsonPrimitive())values.put(setting.getKey(),value.getAsString());
                        });
                        settings.put(envelope.get("id").getAsString(),Map.copyOf(values));
                    }
                } catch(Exception ex){failures.add(entry.getKey()+": "+ex.getMessage());}
            });
        Map<String,BedrockGeometry> geometries = new LinkedHashMap<>();
        Map<String,JsonObject> geometrySources = new LinkedHashMap<>();
        Map<String,BedrockRenderController> controllers = new LinkedHashMap<>();
        Map<String,BedrockClientEntity> entities = new LinkedHashMap<>();
        Map<String,BedrockAnimation> animations = new LinkedHashMap<>();
        Map<String,BedrockAnimationController> animationControllers = new LinkedHashMap<>();
        Map<String,JsonObject> materials=new LinkedHashMap<>();
        next.forEach((id,json) -> {
            try {
                if(id.startsWith("material."))materials.put(id.substring(9),JsonParser.parseString(json).getAsJsonObject());
                else if (id.startsWith("geometry.")) geometrySources.put(id,JsonParser.parseString(json).getAsJsonObject());
                else if (id.startsWith("controller.render.")) controllers.put(id, new BedrockRenderController(JsonParser.parseString(json).getAsJsonObject()));
                else if (id.startsWith("controller.animation.")) animationControllers.put(id, new BedrockAnimationController(JsonParser.parseString(json).getAsJsonObject()));
                else if (id.startsWith("animation.")) animations.put(id, new BedrockAnimation(JsonParser.parseString(json).getAsJsonObject()));
                else if (id.contains(":")) entities.put(id, BedrockClientEntity.parse(JsonParser.parseString(json).getAsJsonObject()));
            } catch (RuntimeException exception) {
                failures.add(id + ": " + exception.getMessage());
            }
        });
        geometries.putAll(dev.matanyah.bedrockcompat.bedrock.entity.BedrockGeometryResolver.resolve(geometrySources,failures::add));
        dev.matanyah.bedrockcompat.bedrock.entity.BedrockPlayerDefaults.geometries(geometries);
        dev.matanyah.bedrockcompat.bedrock.entity.BedrockPlayerDefaults.controllers(controllers);
        snapshot = new Snapshot(Map.copyOf(next), Map.copyOf(geometries), Map.copyOf(controllers), Map.copyOf(entities),
            Map.copyOf(animations), Map.copyOf(animationControllers), textures,
            new EntityPackPriority(VirtualCompatibilityPackProvider.PACK_ID,ranks,javaSources),new dev.matanyah.bedrockcompat.bedrock.entity.BedrockMaterials(materials),Map.copyOf(settings),List.copyOf(failures));
        BedrockCompat.LOGGER.info("Loaded {} native Bedrock entity definitions ({} errors)",next.size(),failures.size());
        failures.forEach(message->BedrockCompat.LOGGER.warn("Native entity resource: {}",message));
    }
}
