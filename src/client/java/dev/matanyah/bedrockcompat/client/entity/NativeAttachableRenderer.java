package dev.matanyah.bedrockcompat.client.entity;

import dev.matanyah.bedrockcompat.bedrock.entity.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Native held-attachable extraction. Mutable Minecraft data never reaches submission callbacks. */
final class NativeAttachableRenderer {
    private record State(String definition,ItemStack item,BedrockEntityInstance runtime,double bornAt) {}
    private static final Map<Entity,Map<String,State>> instances=new WeakHashMap<>();
    private static NativeEntityResources.Snapshot generation;
    private static Map<String,List<BedrockAttachable>> byItem=Map.of();
    private NativeAttachableRenderer() {}
    private static void generation(NativeEntityResources.Snapshot resources) {
        if(generation==resources)return;
        instances.clear();generation=resources;
        Map<String,List<BedrockAttachable>> index=new HashMap<>();
        for(var definition:resources.attachables().values())for(var id:definition.items().keySet())
            index.computeIfAbsent(id,ignored->new ArrayList<>()).add(definition);
        // Explicit item overrides (often owner-specific armor models) precede the
        // item's unconditional default attachable. Conditions still decide applicability.
        index.forEach((id,definitions)->definitions.sort(Comparator.comparing(d->d.model().identifier().equals(id))));
        byItem=Map.copyOf(index);
    }
    private static List<BedrockAttachable> candidates(String item) {
        var direct=byItem.getOrDefault(item,List.of());String alias=BedrockItemIds.bedrock(item);
        if(alias.equals(item))return direct;
        var result=new ArrayList<>(direct);result.addAll(byItem.getOrDefault(alias,List.of()));return result;
    }

    private static String slotName(EquipmentSlot slot) {
        return switch(slot){case MAINHAND->"main_hand";case OFFHAND->"off_hand";
            case HEAD->"head";case CHEST->"torso";case LEGS->"legs";case FEET->"feet";
            default->throw new IllegalArgumentException("Unsupported attachable equipment slot: "+slot);};
    }
    private static Map<String,Object> values(EquipmentSlot slot,NativeEntityRenderer.View view) {
        return Map.of("context.item_slot",slotName(slot),"context.is_first_person",view==NativeEntityRenderer.View.FIRST_PERSON?1d:0d);
    }
    private static Molang.Queries queries(BedrockAttachable definition,Molang.Queries ownerQueries,NativeEntityResources.Snapshot resources) {
        var settings=new EntityQueryContext("","",Set.of(),Map.of("pack_settings",
            resources.packSettings().getOrDefault("attachable."+definition.model().identifier(),Map.of())));
        return (name,args)->name.equals("is_pack_setting_selected")||name.equals("is_pack_setting_enabled")||name.equals("get_pack_setting")
            ?settings.get(name,args):ownerQueries.get(name,args);
    }
    private static Molang.Queries itemQueries(ItemStack item,Molang.Queries fallback) {
        Set<String> tags=new HashSet<>();item.typeHolder().tags().forEach(tag->tags.add(tag.location().toString()));
        return new MolangItemQueries(fallback,BedrockItemIds.tags(dev.matanyah.bedrockcompat.behavior.BehaviorIdentities.itemIdentifier(item),tags),
            item.getMaxDamage(),item.getDamageValue(),item.hasFoil(),item.isStackable());
    }
    /** Refresh parent inputs before its animation pass; retain the child's playback clock. */
    static void prepare(Entity owner,Molang.Queries ownerQueries,NativeEntityResources.Snapshot resources,
            double now,NativeEntityRenderer.View view,Molang.Context holderContext) {
        generation(resources);
        if(!(owner instanceof LivingEntity living))return;
        var states=instances.computeIfAbsent(owner,ignored->new HashMap<>());
        for(var slot:List.of(EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
            String key=view+":"+slotName(slot);var item=living.getItemBySlot(slot);
            if(item.isEmpty()){states.remove(key);continue;}
            String id=dev.matanyah.bedrockcompat.behavior.BehaviorIdentities.itemIdentifier(item);
            BedrockAttachable selected=null;
            for(var candidate:candidates(id))try {
                if(candidate.matches(id,queries(candidate,itemQueries(item,ownerQueries),resources),values(slot,view),holderContext)){selected=candidate;break;}
            }catch(RuntimeException error){NativeEntityResources.runtimeError("Attachable selection "+candidate.model().identifier()+": "+error.getMessage());}
            if(selected==null){states.remove(key);continue;}
            State previous=states.get(key);
            boolean unchanged=previous!=null&&previous.definition.equals(selected.model().identifier())&&ItemStack.matches(previous.item,item);
            var definition=selected;
            try {
                holderContext.withAdditionalBindings(values(slot,view),()-> {
                    for(var script:definition.parentSetup())script.evaluate(holderContext);
                    return null;
                });
                if(!unchanged)states.put(key,new State(definition.model().identifier(),item.copy(),new BedrockEntityInstance(definition.model(),owner.getId(),
                    error->NativeEntityResources.runtimeError(id+": "+error)),now));
            }catch(RuntimeException error){states.remove(key);NativeEntityResources.runtimeError("Attachable parent_setup "+definition.model().identifier()+": "+error.getMessage());}
        }
    }

    static Optional<List<NativeEntityRenderer.Draw>> extract(Entity owner,boolean right,
            BedrockGeometry holder,Map<String,BedrockAnimation.Pose> holderPoses,
            List<BedrockRenderController.Visibility> holderVisibility,Molang.Queries ownerQueries,
            NativeEntityResources.Snapshot resources,double now,NativeEntityRenderer.View view,BedrockEntityInstance holderRuntime) {
        generation(resources);
        if(!(owner instanceof LivingEntity living))return Optional.empty();
        boolean main=right==(living.getMainArm()==HumanoidArm.RIGHT);
        var slot=main?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND;
        return extract(owner,slot,holder,holderPoses,holderVisibility,ownerQueries,resources,now,view,holderRuntime);
    }
    static Optional<List<NativeEntityRenderer.Draw>> extract(Entity owner,EquipmentSlot slot,
            BedrockGeometry holder,Map<String,BedrockAnimation.Pose> holderPoses,
            List<BedrockRenderController.Visibility> holderVisibility,Molang.Queries ownerQueries,
            NativeEntityResources.Snapshot resources,double now,NativeEntityRenderer.View view,BedrockEntityInstance holderRuntime) {
        if(!(owner instanceof LivingEntity living))return Optional.empty();
        boolean main=slot==EquipmentSlot.MAINHAND;
        String slotName=slotName(slot),key=view+":"+slotName;
        var states=instances.computeIfAbsent(owner,ignored->new HashMap<>());
        ItemStack item=living.getItemBySlot(slot);
        if(item.isEmpty()){states.remove(key);return Optional.empty();}
        String itemId=dev.matanyah.bedrockcompat.behavior.BehaviorIdentities.itemIdentifier(item);
        Map<String,Object> values=Map.of("context.item_slot",slotName,
            "context.is_first_person",view==NativeEntityRenderer.View.FIRST_PERSON?1d:0d);
        var anchors=BedrockGeometryMesh.anchors(holder,holderPoses);
        Molang.Queries itemQueries=(name,args)->switch(name) {
            case "is_enchanted"->item.hasFoil()?1d:0d;
            case "is_attached"->1d;
            case "item_slot_to_bone_name"->{
                if(args.size()!=1)throw new IllegalArgumentException("item_slot_to_bone_name requires one slot");
                String requested=args.getFirst().toString();
                if(!requested.equals("main_hand")&&!requested.equals("off_hand")) {
                    String semantic=switch(requested){case "head"->"head";case "torso"->"body";
                        case "legs","feet"->"body";default->throw new IllegalArgumentException("Unsupported attachable slot: "+requested);};
                    String bone=NativeEntityEquipment.boneName(anchors,semantic);
                    if(bone==null)throw new IllegalArgumentException("Missing native item-slot bone: "+requested);
                    yield bone;
                }
                boolean requestedMain=requested.equals("main_hand");
                boolean requestedRight=requestedMain==(living.getMainArm()==HumanoidArm.RIGHT);
                String bone=NativeEntityEquipment.boneName(anchors,requestedRight?"rightitem":"leftitem");
                if(bone==null)throw new IllegalArgumentException("Missing native item-slot bone: "+requested);
                yield bone;
            }
            default->ownerQueries.get(name,args);
        };
        for(var definition:candidates(itemId)) {
            try {
                Molang.Queries queries=queries(definition,itemQueries(item,itemQueries),resources);
                if(!holderRuntime.withContext(ownerQueries,holderContext->definition.matches(itemId,queries,values,holderContext)))continue;
                State state=states.get(key);
                if(state==null||!state.definition.equals(definition.model().identifier())||!ItemStack.matches(state.item,item))continue;
                var current=state;
                var queryBindings=new HashMap<String,org.joml.Matrix4f>();
                var frame=holderRuntime.withContext(ownerQueries,holderContext->current.runtime.withOwner(holderContext,()->
                    current.runtime.withBindings(queryBindings,()->
                    current.runtime.evaluateFrame(queries,resources.renderControllers(),resources.animations(),
                    resources.animationControllers(),OptionalInt.empty(),Math.max(0,now-current.bornAt),resources.geometries(),
                    Map.of("is_enchanted",item.hasFoil()?1d:0d),values,childContext->{
                        Object id=definition.model().aliases().get("geometry."+definition.model().queryableGeometry());
                        var queryGeometry=resources.geometries().get(id);
                        if(queryGeometry!=null)queryBindings.putAll(BedrockAttachableBindings.resolve(queryGeometry,childContext,anchors,holderVisibility));
                    }))));
                List<NativeEntityRenderer.Draw> draws=new ArrayList<>();
                var particleAnchors=new HashMap<String,org.joml.Matrix4f>();
                for(var selection:frame.selections()) {
                    var geometry=resources.geometries().get(selection.geometry());
                    if(geometry==null)throw new IllegalArgumentException("Missing attachable geometry: "+selection.geometry());
                    var bindings=holderRuntime.withContext(ownerQueries,holderContext->current.runtime.withOwner(holderContext,()->
                        current.runtime.withContext(queries,childContext->childContext.withAdditionalBindings(values,()->
                            BedrockAttachableBindings.resolve(geometry,childContext,anchors,holderVisibility)))));
                    bindings.values().forEach(matrix->matrix.scale((float)frame.scale().x(),(float)frame.scale().y(),(float)frame.scale().z()));
                    particleAnchors.putAll(BedrockGeometryMesh.anchors(geometry,frame.poses(),bindings));
                    var layers=new ArrayList<NativeTextureMeshes.Layer>();
                    layers.add(new NativeTextureMeshes.Layer(geometry,null));
                    layers.addAll(NativeTextureMeshes.layers(geometry,definition.model().aliases(),resources));
                    for(var layer:layers) {
                    var layerBindings=layer.texture()==null?bindings:BedrockAttachableBindings.textureBindings(geometry,bindings);
                    var mesh=BedrockGeometryMesh.bake(layer.geometry(),frame.poses(),selection.visibility(),layerBindings);
                    Map<String,List<BedrockGeometryMesh.Quad>> materials=new LinkedHashMap<>();
                    for(var quad:mesh) {
                        String material="entity_alphatest";
                        for(var rule:selection.materials())if(BedrockGeometryMesh.matches(rule.bonePattern(),quad.bone()))material=rule.identifier();
                        materials.computeIfAbsent(material,ignored->new ArrayList<>()).add(quad);
                    }
                    for(var entry:materials.entrySet()) {
                        var material=resources.materials().resolve(entry.getKey());
                        material.warnings().forEach(error->NativeEntityResources.runtimeError(itemId+": "+error));
                        List<Identifier> textures=new ArrayList<>();
                        // A render-controller texture list is shader sampler input, not
                        // several overlaid copies of the mesh. Plain entity shaders use #0.
                        var sampledTextures=material.masked()?selection.textures():selection.textures().stream().limit(1).toList();
                        for(int sampler=0;sampler<sampledTextures.size();sampler++) {
                            String texture=sampler==0&&layer.texture()!=null?layer.texture():sampledTextures.get(sampler);
                            var id=Identifier.fromNamespaceAndPath("bedrock_native",texture.endsWith(".png")?texture:texture+".png");
                            if(!resources.textures().contains(id))throw new IllegalArgumentException("Missing attachable texture: "+texture);
                            textures.add(id);
                        }
                        if(material.masked()) {
                            if(textures.size()!=2)throw new IllegalArgumentException("Masked attachable requires two textures");
                            textures=List.of(NativeEntityTextures.masked(textures.get(0),textures.get(1)));
                        }
                        for(var texture:textures) {
                            var id=material.colorMask()?NativeEntityTextures.dyed(texture,0xffffff,material.alphaTest()):texture;
                            draws.add(new NativeEntityRenderer.Draw(id,material,List.copyOf(entry.getValue()),selection.appearance()));
                        }
                    }
                    }
                }
                if(view!=NativeEntityRenderer.View.UI)
                    dev.matanyah.bedrockcompat.client.particle.NativeParticles.attachable(owner,slotName,particleAnchors,frame.effects());
                if(frame.effects().stream().anyMatch(e->e.kind()==BedrockEntityEffect.Kind.SOUND))
                    NativeEntityResources.runtimeError(itemId+": Attachable sound effects are not yet supported");
                return Optional.of(List.copyOf(draws));
            } catch(RuntimeException error) {
                NativeEntityResources.runtimeError("Attachable "+definition.model().identifier()+": "+error.getMessage());
            }
        }
        states.remove(key);
        return Optional.empty();
    }
}
