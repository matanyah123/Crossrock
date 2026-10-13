package dev.matanyah.bedrockcompat.client.entity;

import dev.matanyah.bedrockcompat.bedrock.entity.*;
import dev.matanyah.bedrockcompat.entity.BedrockArmorStandAccess;
import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;

/** Extracts immutable mesh submissions. Rendering callbacks never access an entity or a world. */
public final class NativeEntityRenderer {
    public record Draw(Identifier texture, BedrockMaterials.Material material, List<BedrockGeometryMesh.Quad> mesh,
                       BedrockRenderController.Appearance appearance) {}
    public record Attachment(org.joml.Matrix4fc transform,net.minecraft.client.renderer.item.ItemStackRenderState item,
                             net.minecraft.client.renderer.block.BlockModelRenderState block,boolean fullBright) {
        public Attachment(org.joml.Matrix4fc transform,net.minecraft.client.renderer.item.ItemStackRenderState item,
                          net.minecraft.client.renderer.block.BlockModelRenderState block){this(transform,item,block,false);}
        public Attachment(org.joml.Matrix4fc transform,net.minecraft.client.renderer.item.ItemStackRenderState item) {this(transform,item,null);}
    }
    public record Frame(List<Draw> draws,List<Attachment> attachments,float yaw,float pitch,BedrockAnimation.Vector scale,
                        List<NativeEntityEquipment.Piece> equipment,double originY,Set<net.minecraft.world.entity.HumanoidArm> nativeHands) {
        public Frame(List<Draw> draws,List<Attachment> attachments,float yaw,float pitch,BedrockAnimation.Vector scale,
                     List<NativeEntityEquipment.Piece> equipment,double originY) {this(draws,attachments,yaw,pitch,scale,equipment,originY,Set.of());}
        public Frame(List<Draw> draws,List<Attachment> attachments,float yaw,float pitch,BedrockAnimation.Vector scale,
                     List<NativeEntityEquipment.Piece> equipment) {this(draws,attachments,yaw,pitch,scale,equipment,0);}
        public Frame(List<Draw> draws,List<Attachment> attachments,float yaw,float pitch,BedrockAnimation.Vector scale) {
            this(draws,attachments,yaw,pitch,scale,List.of());
        }
    }
    private static final class Instance {
        final BedrockEntityInstance runtime;
        final double bornAt;
        double lastAt;
        Instance(BedrockEntityInstance runtime,double now){this.runtime=runtime;bornAt=now;lastAt=now;}
    }
    private static final ThreadLocal<Boolean> extractingState=ThreadLocal.withInitial(()->false);
    /** UI and hand consumers extract their own view after Java has prepared its state. */
    public static <T> T withoutWorldExtraction(java.util.function.Supplier<T> action) {
        boolean previous=extractingState.get();extractingState.set(true);
        try{return action.get();}finally{extractingState.set(previous);}
    }
    public static boolean skipsWorldExtraction(){return extractingState.get();}
    public enum View { WORLD, FIRST_PERSON, UI }
    private static final Map<Entity,EnumMap<View,Instance>> instances = new WeakHashMap<>();
    private static NativeEntityResources.Snapshot generation;
    private static net.minecraft.client.multiplayer.ClientLevel instanceLevel;
    private NativeEntityRenderer() {}
    /** Integration boundary for Molang actor references; only authored public variables are exposed. */
    public static Optional<Molang.Context> publicContext(Entity entity) {
        if(generation!=NativeEntityResources.snapshot()||entity.level()!=Minecraft.getInstance().level||entity.isRemoved())return Optional.empty();
        var views=instances.get(entity);var instance=views==null?null:views.get(View.WORLD);
        return instance==null?Optional.empty():Optional.of(instance.runtime.publicContext());
    }
    private static List<Draw> heldDraws(List<Draw> draws,boolean fullBright) {
        if(!fullBright)return draws;
        return draws.stream().map(d->{var a=d.appearance();return new Draw(d.texture(),d.material(),d.mesh(),
            new BedrockRenderController.Appearance(a.color(),a.offsetU(),a.offsetV(),a.scaleU(),a.scaleV(),true,a.replaceHurtOverlay()));}).toList();
    }

    public static Frame extract(Entity entity, EntityRenderState state, float partialTick, Identifier javaTexture) {
        return extract(entity,state,partialTick,javaTexture,View.WORLD);
    }
    public static Frame extract(Entity entity, EntityRenderState state, float partialTick, Identifier javaTexture,View view) {
        if(entity instanceof dev.matanyah.bedrockcompat.behavior.BehaviorEntity actor&&!dev.matanyah.bedrockcompat.client.behavior.BehaviorClientSession.ownsGeneration(actor.stateGeneration()))return null;
        var resources = NativeEntityResources.snapshot();
        if (generation != resources||instanceLevel!=Minecraft.getInstance().level) {
            instances.clear();generation=resources;instanceLevel=Minecraft.getInstance().level;
        }
        instances.keySet().removeIf(Entity::isRemoved);
        String type = entity instanceof dev.matanyah.bedrockcompat.behavior.BehaviorEntity actor?actor.bedrockIdentifier():BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        var definition = resources.entities().get(type);
        if(definition==null)definition=resources.entities().get(BedrockEntityIds.bedrock(type));
        if (definition == null) return null;
        try {
            double age = (entity.tickCount + (double)partialTick) / 20.0;
            double now = view==View.UI?System.nanoTime()/1_000_000_000d:age;
            final var resolvedDefinition=definition;
            Map<String,Object> facts = new HashMap<>();
            facts.put("owner_identifier",resolvedDefinition.identifier());
            facts.put("pack_settings",resources.packSettings().getOrDefault(resolvedDefinition.identifier(),Map.of()));
            facts.put("life_time",age);
            facts.put("is_on_ground",entity.onGround()?1d:0d);
            facts.put("is_in_water",entity.isInWater()?1d:0d); facts.put("is_riding",entity.isPassenger()?1d:0d);
            Set<String> vehicles=new HashSet<>();
            if(entity.getVehicle()!=null) {
                var vehicle=entity.getVehicle();
                String vehicleType=BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString();
                vehicles.add(vehicleType);
                vehicles.add(BedrockEntityIds.bedrock(vehicleType));
            }
            facts.put("vehicle_types",Set.copyOf(vehicles));
            facts.put("is_sneaking",entity.isShiftKeyDown()?1d:0d);
            facts.put("is_alive",entity.isAlive()?1d:0d);
            facts.put("is_baby",0d);facts.put("is_sleeping",0d);
            facts.put("is_attached",0d);facts.put("is_on_screen",1d);
            facts.put("is_on_fire",entity.isOnFire()?1d:0d);
            facts.put("ground_speed",entity.getDeltaMovement().horizontalDistance()*20);
            facts.put("vertical_speed",entity.getDeltaMovement().y*20);
            facts.put("body_y_rotation",(double)entity.getYRot(partialTick)); facts.put("head_x_rotation",(double)entity.getXRot(partialTick));
            facts.put("head_y_rotation",0d); facts.put("is_in_ui",0d);
            facts.put("position",List.of(entity.getX(),entity.getY(),entity.getZ()));
            facts.put("position_delta",List.of(entity.getDeltaMovement().x,entity.getDeltaMovement().y,entity.getDeltaMovement().z));
            var cameraPosition=Minecraft.getInstance().gameRenderer.mainCamera().position();
            facts.put("camera_delta",List.of(cameraPosition.x-entity.getX(),cameraPosition.y-entity.getY(),cameraPosition.z-entity.getZ()));
            // This extraction hook is world/third-person rendering, never a first-person hand pass.
            facts.put("is_first_person",0d);
            facts.put("distance_from_camera",Math.sqrt(state.distanceToCameraSq));
            NativeEntityFacts.addCommon(entity,partialTick,facts);
            if(entity instanceof LivingEntity living&&state instanceof LivingEntityRenderState livingState) {
                facts.put("is_baby",living.isBaby()?1d:0d);facts.put("is_sleeping",living.isSleeping()?1d:0d);
                facts.put("sleep_rotation",livingState.bedOrientation==null?0d:(double)livingState.bedOrientation.toYRot());
                facts.put("health",(double)living.getHealth());facts.put("max_health",(double)living.getMaxHealth());
                facts.put("hurt_direction",(double)living.getHurtDir());facts.put("hurt_time",living.hurtTime/20d);
                facts.put("body_y_rotation",(double)livingState.bodyRot);facts.put("head_x_rotation",(double)livingState.xRot);
                // Java stores head yaw relative to the body; Bedrock exposes world yaw.
                facts.put("head_y_rotation",BedrockModelSpace.headYaw(livingState.bodyRot,livingState.yRot));
                NativeEntityFacts.add(living,livingState,partialTick,facts);
            }
            NativeEntityRelations.add(entity,partialTick,facts);
            var boat=BedrockEntityIds.boat(type);
            if(boat.isPresent()) {
                facts.put("variant",(double)boat.get().variant());
                facts.put("body_y_rotation",(double)BedrockEntityIds.bodyYaw(type,entity.getYRot(partialTick)));
                facts.put("head_x_rotation",0d);
            }
            if(entity instanceof net.minecraft.world.entity.animal.fish.Pufferfish fish)facts.put("puff_state",fish.getPuffState());
            if(entity instanceof net.minecraft.world.entity.animal.fish.TropicalFish fish)
                facts.put("large_fish",fish.getPattern().base()==net.minecraft.world.entity.animal.fish.TropicalFish.Base.LARGE);
            if(entity instanceof LivingEntity living)facts.put("has_body_equipment",!living.getItemBySlot(EquipmentSlot.BODY).isEmpty());
            if(entity instanceof net.minecraft.world.entity.TamableAnimal animal)facts.put("is_tamed",animal.isTame());
            if(entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither)facts.put("is_powered",wither.isPowered());
            boolean baby=entity instanceof net.minecraft.world.entity.decoration.ArmorStand stand?stand.isSmall()
                :entity instanceof LivingEntity living&&living.isBaby();
            if(!resources.priority().owns(resolvedDefinition.identifier(),javaTexture.toString(),EntityModelNames.candidates(type,baby,facts)))return null;
            var instance = instances.computeIfAbsent(entity,key -> new EnumMap<>(View.class)).computeIfAbsent(view,key -> new Instance(new BedrockEntityInstance(resolvedDefinition,
                entity.getUUID().getMostSignificantBits()^entity.getUUID().getLeastSignificantBits(),
                message->NativeEntityResources.runtimeError(type+": "+message)),now));
            // Use this view's actual evaluation interval, not a shared render timer.
            facts.put("delta_time",Math.max(0,now-instance.lastAt));
            Map<String,Object> engineVariables=Map.of();Map<String,Object> runtimeAliases=new HashMap<>();
            Map<String,Identifier> runtimeTextures=new HashMap<>();
            if(entity instanceof net.minecraft.client.player.AbstractClientPlayer player) {
                engineVariables=NativePlayerInputs.add(player,partialTick,view,facts);
                Object skin=resolvedDefinition.aliases().get("texture.default");
                if(Set.of("textures/entity/steve","textures/entity/alex").contains(skin)) {
                    runtimeAliases.put("texture.default","bedrock_runtime:skin");
                    runtimeTextures.put("bedrock_runtime:skin",player.getSkin().body().texturePath());
                }
                if(player.getSkin().cape()!=null&&resolvedDefinition.aliases().containsKey("texture.cape")) {
                    runtimeAliases.put("texture.cape","bedrock_runtime:cape");
                    runtimeTextures.put("bedrock_runtime:cape",player.getSkin().cape().texturePath());
                }
                if("geometry.humanoid.custom".equals(resolvedDefinition.aliases().get("geometry.default"))
                        &&!resources.definitions().containsKey("geometry.humanoid.custom")
                        &&player.getSkin().model()==net.minecraft.world.entity.player.PlayerModelType.SLIM)
                    runtimeAliases.put("geometry.default","bedrock_compat:humanoid_slim");
            }
            var biome = entity.level().getBiome(entity.blockPosition());
            Set<String> tags = new HashSet<>(); biome.tags().forEach(tag->tags.add(tag.location().toString()));
            String biomeId=biome.unwrapKey().map(k->k.identifier().toString()).orElse("");
            tags.addAll(resources.biomeTags().getOrDefault(BedrockBiomes.identifier(biomeId),Set.of()));
            facts.put("query_provider",NativeMolangQueries.snapshot(entity));
            if(entity instanceof dev.matanyah.bedrockcompat.behavior.BehaviorEntity actor) {Map<String,Object> properties=new HashMap<>();actor.clientProperties().entrySet().forEach(e->{var v=e.getValue().getAsJsonPrimitive();properties.put(e.getKey(),v.isBoolean()?v.getAsBoolean():v.isNumber()?v.getAsDouble():v.getAsString());});facts.put("properties",Map.copyOf(properties));}
            var context = new EntityQueryContext(entity.getName().getString(),biome.unwrapKey().map(k->k.identifier().toString()).orElse(""),tags,facts);
            Map<String,EntityFrameQueries.Item> equipment = new HashMap<>();
            Map<String,EquipmentSlot> slots=Map.of("slot.weapon.mainhand",EquipmentSlot.MAINHAND,"slot.weapon.offhand",EquipmentSlot.OFFHAND,
                "slot.armor.head",EquipmentSlot.HEAD,"slot.armor.chest",EquipmentSlot.CHEST,"slot.armor.legs",EquipmentSlot.LEGS,"slot.armor.feet",EquipmentSlot.FEET,"slot.armor.body",EquipmentSlot.BODY);
            if(entity instanceof LivingEntity living)slots.forEach((name,slot)-> {
                var stack=entity instanceof net.minecraft.world.entity.monster.cubemob.SulfurCube cube&&slot==EquipmentSlot.MAINHAND
                    ?cube.getBodyArmorItem():living.getItemBySlot(slot);Set<String> itemTags=new HashSet<>();
                stack.typeHolder().tags().forEach(tag->itemTags.add(tag.location().toString()));
                equipment.put(name,new EntityFrameQueries.Item(dev.matanyah.bedrockcompat.behavior.BehaviorIdentities.itemIdentifier(stack),itemTags,
                    stack.getDamageValue(),living.isUsingItem()&&living.getUsedItemHand()==(slot==EquipmentSlot.OFFHAND?
                        net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND)
                        &&(slot==EquipmentSlot.MAINHAND||slot==EquipmentSlot.OFFHAND)?living.getUseItemRemainingTicks()/20d:0));
            });
            var camera=Minecraft.getInstance().gameRenderer.mainCamera();
            String graphics=Minecraft.getInstance().options.graphicsPreset().get()==net.minecraft.client.GraphicsPreset.FAST?"simple":"fancy";
            var queryPosition=(List<?>)facts.get("position");
            Molang.Queries frameQueries = new EntityEnvironmentQueries(
                new EntityFrameQueries(context,equipment,camera.xRot(),camera.yRot(),graphics),(x,y,z,tag)-> {
                    var pos=new net.minecraft.core.BlockPos(x,y,z);
                    var id=Identifier.tryParse(BedrockBlockTags.javaTag(tag));
                    // Observational only: resource-pack expressions must never load chunks.
                    if(!entity.level().hasChunkAt(pos))return false;
                    var block=entity.level().getBlockState(pos);
                    if(tag.equals("metal"))return Set.of("iron_bars","chain","iron_chain","lantern","soul_lantern").contains(
                        BuiltInRegistries.BLOCK.getKey(block.getBlock()).getPath());
                    return id!=null&&block.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,id));
                },Molang.number(queryPosition.get(0)),Molang.number(queryPosition.get(1)),Molang.number(queryPosition.get(2)));
            Molang.Queries queries=(name,args)-> {
                try{return frameQueries.get(name,args);}
                catch(IllegalArgumentException unsupported) {
                    // Bedrock Molang reports unavailable query data and yields zero. Preserve valid
                    // geometry/animation rather than throwing away an entire entity for one capability.
                    if(!unsupported.getMessage().startsWith("Unsupported Bedrock"))throw unsupported;
                    NativeEntityResources.runtimeError(type+": "+unsupported.getMessage()+" (using Molang error value 0)");
                    return 0d;
                }
            };
            OptionalInt pose=entity instanceof BedrockArmorStandAccess access && access.bedrockCompatState().active()
                ?OptionalInt.of(access.bedrockCompatState().poseIndex()):OptionalInt.empty();
            var frame = instance.runtime.evaluateFrame(queries,resources.renderControllers(),resources.animations(),
                resources.animationControllers(),pose,Math.max(0,now-instance.bornAt),resources.geometries(),engineVariables,runtimeAliases,
                holderContext->{if(resolvedDefinition.enableAttachables())NativeAttachableRenderer.prepare(entity,queries,resources,now,view,holderContext);});
            instance.lastAt=Math.max(instance.lastAt,now);
            boolean localFirstPerson=entity==Minecraft.getInstance().player&&Minecraft.getInstance().options.getCameraType().isFirstPerson();
            boolean audible=view==View.FIRST_PERSON?localFirstPerson:view==View.WORLD&&!localFirstPerson;
            if(view!=View.UI) {
                var particleAnchors=new HashMap<String,org.joml.Matrix4f>();
                for(var selection:frame.selections()) {
                    var geometry=resources.geometries().get(selection.geometry());
                    if(geometry!=null)particleAnchors.putAll(instance.runtime.anchors(geometry,frame.poses()));
                }
                dev.matanyah.bedrockcompat.client.particle.NativeParticles.observe(entity,queries,
                    instance.runtime.withContext(queries,Molang.Context::variables),particleAnchors,Molang.number(facts.get("body_y_rotation")),frame.scale(),
                    entity instanceof net.minecraft.world.entity.decoration.ArmorStand&&entity.hasCustomName()
                        ?dev.matanyah.bedrockcompat.entity.ArmorStandDisplayMode.fromName(entity.getCustomName().getString()).originY():0);
            }
            for(var effect:frame.effects()) {
                if(effect.kind()==BedrockEntityEffect.Kind.SOUND) {
                    if(!audible)continue;
                    var id=NativeSoundEvents.id(effect.effect());
                    var manager=Minecraft.getInstance().getSoundManager();
                    var soundPosition=dev.matanyah.bedrockcompat.client.particle.NativeParticles.locatorPosition(entity,effect.locator());
                    if(!effect.locator().isEmpty()&&soundPosition.isEmpty()) {
                        NativeEntityResources.runtimeError(type+": Missing sound locator: "+effect.locator());continue;
                    }
                    var position=soundPosition.orElseGet(()->new org.joml.Vector3f((float)entity.getX(),(float)entity.getY(),(float)entity.getZ()));
                    if(manager.getSoundEvent(id)==null)NativeEntityResources.runtimeError(type+": Missing sound event: "+effect.effect());
                    else manager.play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                        net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id),net.minecraft.sounds.SoundSource.NEUTRAL,
                        1,1,entity.getRandom(),position.x,position.y,position.z));
                } else if(view!=View.UI)dev.matanyah.bedrockcompat.client.particle.NativeParticles.emit(entity,effect,
                    effect.effect().equals(resolvedDefinition.aliases().get("particle.fire")));
            }
            List<Draw> draws = new ArrayList<>();
            List<Attachment> attachments=new ArrayList<>();boolean attached=false;
            boolean hideHeldItems=instance.runtime.withContext(queries,c->Molang.truth(resolvedDefinition.hideHeldItems().evaluate(c)));
            List<NativeEntityEquipment.Piece> equippedParts=new ArrayList<>();
            Set<EquipmentSlot> nativeArmor=EnumSet.noneOf(EquipmentSlot.class);
            Set<net.minecraft.world.entity.HumanoidArm> nativeHands=EnumSet.noneOf(net.minecraft.world.entity.HumanoidArm.class);
            BedrockGeometry equipmentGeometry=null;
            List<BedrockRenderController.Visibility> equipmentVisibility=List.of();
            for(var selection:frame.selections()) {
                var geometry=resources.geometries().get(selection.geometry());
                if(geometry==null)throw new IllegalArgumentException("Missing geometry: "+selection.geometry());
                if(!attached&&!hideHeldItems&&entity instanceof net.minecraft.world.entity.monster.cubemob.SulfurCube cube
                        &&!cube.getBodyArmorItem().isEmpty()) {
                    var anchor=NativeEntityEquipment.handAnchor(BedrockGeometryMesh.anchors(geometry,frame.poses()),true,geometry,selection.visibility());
                    if(anchor!=null) {
                        var item=cube.getBodyArmorItem();
                        var blockState=item.getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_STATE,
                            net.minecraft.world.item.component.BlockItemStateProperties.EMPTY)
                            .apply(net.minecraft.world.level.block.Block.byItem(item.getItem()).defaultBlockState());
                        var block=new net.minecraft.client.renderer.block.BlockModelRenderState();
                        new net.minecraft.client.renderer.block.BlockModelResolver(Minecraft.getInstance().getModelManager())
                            .update(block,blockState,net.minecraft.client.renderer.entity.SulfurCubeRenderer.BLOCK_DISPLAY_CONTEXT);
                        // Preserve the bottom-center anchor, but compensate handedness
                        // for Java block vertices (native geometry reverses winding itself).
                        var transform=BedrockModelSpace.containedBlockTransform(anchor,frame.heldItemScale());
                        attachments.add(new Attachment(transform,null,block));attached=true;
                    }
                }
                if(!attached&&!hideHeldItems&&state instanceof net.minecraft.client.renderer.entity.state.ArmedEntityRenderState armed) {
                    var anchors=BedrockGeometryMesh.anchors(geometry,frame.poses());
                    boolean nativeRight=false,nativeLeft=false;
                    if(resolvedDefinition.enableAttachables()) {
                        var right=NativeAttachableRenderer.extract(entity,true,geometry,frame.poses(),selection.visibility(),queries,resources,now,view,instance.runtime);
                        var left=NativeAttachableRenderer.extract(entity,false,geometry,frame.poses(),selection.visibility(),queries,resources,now,view,instance.runtime);
                        if(right.isPresent()){draws.addAll(heldDraws(right.get(),resolvedDefinition.heldItemIgnoresLighting()));nativeRight=true;nativeHands.add(net.minecraft.world.entity.HumanoidArm.RIGHT);}
                        if(left.isPresent()){draws.addAll(heldDraws(left.get(),resolvedDefinition.heldItemIgnoresLighting()));nativeLeft=true;nativeHands.add(net.minecraft.world.entity.HumanoidArm.LEFT);}
                    }
                    if(!nativeRight)attach(attachments,NativeEntityEquipment.handAnchor(anchors,true,geometry,selection.visibility()),armed.rightHandItemState,frame.heldItemScale());
                    if(!nativeLeft)attach(attachments,NativeEntityEquipment.handAnchor(anchors,false,geometry,selection.visibility()),armed.leftHandItemState,frame.heldItemScale());
                    attached=nativeRight||nativeLeft||!attachments.isEmpty();
                }
                if(resolvedDefinition.enableAttachables()&&!resolvedDefinition.hideArmor()&&view!=View.FIRST_PERSON)
                    for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
                        if(nativeArmor.contains(slot))continue;
                        var equipmentDraws=NativeAttachableRenderer.extract(entity,slot,geometry,frame.poses(),selection.visibility(),queries,resources,now,view,instance.runtime);
                        if(equipmentDraws.isPresent()){draws.addAll(equipmentDraws.get());nativeArmor.add(slot);}
                    }
                if(equipmentGeometry==null){equipmentGeometry=geometry;equipmentVisibility=selection.visibility();}
                var layers=new ArrayList<NativeTextureMeshes.Layer>();
                layers.add(new NativeTextureMeshes.Layer(geometry,null));
                var textureAliases=new HashMap<String,Object>(resolvedDefinition.aliases());
                textureAliases.putAll(runtimeAliases);
                layers.addAll(NativeTextureMeshes.layers(geometry,textureAliases,resources));
                for(var layer:layers) {
                var mesh=BedrockGeometryMesh.bake(layer.geometry(),frame.poses(),selection.visibility());
                List<Identifier> selectedTextures=new ArrayList<>();
                for(int sampler=0;sampler<selection.textures().size();sampler++) {
                    String texture=sampler==0&&layer.texture()!=null?layer.texture():selection.textures().get(sampler);
                    Identifier id=runtimeTextures.get(texture);
                    if(id==null) {
                        id=Identifier.fromNamespaceAndPath("bedrock_native",texture.endsWith(".png")?texture:texture+".png");
                        if(!resources.textures().contains(id))throw new IllegalArgumentException("Missing native texture: "+texture);
                    }
                    selectedTextures.add(id);
                }
                Map<String,List<BedrockGeometryMesh.Quad>> materials=new LinkedHashMap<>();
                for(var quad:mesh) {
                    String material="entity_alphatest";
                    for(var rule:selection.materials())if(BedrockGeometryMesh.matches(rule.bonePattern(),quad.bone()))material=rule.identifier();
                    materials.computeIfAbsent(material,key->new ArrayList<>()).add(quad);
                }
                materials.forEach((material,quads)-> {
                    var resolved=resources.materials().resolve(material);
                    resolved.warnings().forEach(warning->NativeEntityResources.runtimeError(type+": "+material+": "+warning));
                    // Controller textures are shader samplers, not duplicate mesh layers.
                    List<Identifier> drawTextures=selectedTextures.stream().limit(1).toList();
                    if(resolved.masked()) {
                        if(selectedTextures.size()!=2)throw new IllegalArgumentException("Masked material requires exactly two textures: "+material);
                        drawTextures=List.of(NativeEntityTextures.masked(selectedTextures.get(0),selectedTextures.get(1)));
                    }
                    var quadsSnapshot=entity instanceof net.minecraft.world.entity.monster.cubemob.SulfurCube
                        ?SulfurCubeGeometry.opaqueCore(layer.geometry(),quads,resolved):List.copyOf(quads);
                    if(entity instanceof net.minecraft.world.entity.monster.cubemob.SulfurCube cube
                            &&cube.getBodyArmorItem().is(net.minecraft.world.item.Items.MAGMA_BLOCK))
                        quadsSnapshot=SulfurCubeGeometry.magmaShell(layer.geometry(),quadsSnapshot,resolved);
                    int dye=entity instanceof net.minecraft.world.entity.animal.sheep.Sheep sheep?sheep.getColor().getTextureDiffuseColor():0xffffff;
                    for(var drawTexture:drawTextures)draws.add(new Draw(resolved.colorMask()
                        ?NativeEntityTextures.dyed(drawTexture,dye,resolved.alphaTest()):drawTexture,resolved,quadsSnapshot,selection.appearance()));
                });
                }
            }
            // Resolve all native equipment layers before considering Java fallback. A later
            // controller may supply an armor slot that the first controller intentionally omits.
            if(!resolvedDefinition.hideArmor()&&equipmentGeometry!=null&&view!=View.FIRST_PERSON
                    &&state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState humanoidState)
                equippedParts.addAll(NativeEntityEquipment.extract(humanoidState,
                    BedrockGeometryMesh.anchors(equipmentGeometry,frame.poses()),equipmentVisibility,nativeArmor));
            // A valid render controller may deliberately hide every bone, or its condition
            // may select no layer this frame. An empty native frame is not a missing model:
            // falling back here would incorrectly flash the vanilla entity into view.
            if(draws.isEmpty()&&resolvedDefinition.controllers().stream().noneMatch(c->resources.renderControllers().containsKey(c.identifier())))return null;
            double originY=entity instanceof net.minecraft.world.entity.decoration.ArmorStand&&entity.hasCustomName()
                ?dev.matanyah.bedrockcompat.entity.ArmorStandDisplayMode.fromName(entity.getCustomName().getString()).originY():0;
            if(draws.stream().anyMatch(d->d.material.stencil()!=null)) {
                var eye=BedrockModelSpace.camera(cameraPosition.x-entity.getX(),cameraPosition.y-entity.getY(),
                    cameraPosition.z-entity.getZ(),Molang.number(facts.get("body_y_rotation")));
                eye.y-=(float)(originY*16);
                if(frame.scale().x()!=0&&frame.scale().y()!=0&&frame.scale().z()!=0)
                    eye.div((float)frame.scale().x(),(float)frame.scale().y(),(float)frame.scale().z());
                var clipped=BedrockPortalClipper.clip(draws.stream().map(d->new BedrockPortalClipper.Surface(d.material,d.mesh)).toList(),eye);
                var visible=new ArrayList<Draw>();
                for(int i=0;i<draws.size();i++)if(!clipped.get(i).isEmpty()) {
                    var draw=draws.get(i);visible.add(new Draw(draw.texture,draw.material,clipped.get(i),draw.appearance));
                }
                draws.clear();draws.addAll(visible);
            }
            if(resolvedDefinition.heldItemIgnoresLighting())attachments.replaceAll(a->new Attachment(a.transform,a.item,a.block,true));
            return new Frame(List.copyOf(draws),List.copyOf(attachments),BedrockEntityIds.bodyYaw(type,entity.getYRot(partialTick)),
                boat.isPresent()?0:entity.getXRot(partialTick),frame.scale(),List.copyOf(equippedParts),originY,Set.copyOf(nativeHands));
        } catch(RuntimeException exception) {
            NativeEntityResources.runtimeError(type+": "+exception.getMessage());
            return null;
        }
    }

    public static void submit(Frame frame, PoseStack poses, SubmitNodeCollector collector, int light,int overlay,int color,int outline) {
        submit(frame,poses,collector,light,overlay,color,outline,false);
    }
    public static void submit(Frame frame, PoseStack poses, SubmitNodeCollector collector, int light,int overlay,int color,int outline,boolean outlineOnly) {
        submit(frame,poses,collector,light,overlay,color,outline,outlineOnly,null);
    }
    public static void submit(Frame frame, PoseStack poses, SubmitNodeCollector collector, int light,int overlay,int color,int outline,boolean outlineOnly,
                              net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer equipmentRenderer) {
        poses.pushPose();
        try {
        // Display origin is presentation-only: never move the entity, hitbox or synchronized pose.
        poses.translate(0,-frame.originY,0);
        poses.translate(0,1.5,0);poses.scale((float)frame.scale.x(),(float)frame.scale.y(),(float)frame.scale.z());poses.translate(0,-1.5,0);
        for(var draw:frame.draws) {
            var appearance=draw.appearance;var tint=appearance.color();
            int rgba=channel(color>>>24,tint.a())<<24|channel(color>>>16&255,tint.r())<<16
                |channel(color>>>8&255,tint.g())<<8|channel(color&255,tint.b());
            int drawLight=appearance.fullBright()?net.minecraft.util.LightCoordsUtil.FULL_BRIGHT:light;
            int drawOverlay=appearance.replaceHurtOverlay()?net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY:overlay;
            // Emission controls lighting, not transparency. Emissive alpha-test
            // cubes still need opaque depth writes, or back faces overwrite fronts.
            var type=NativeMaterialRenderTypes.get(draw.texture,draw.material,(rgba>>>24)<255);
            if(draw.material.stencil()!=null&&draw.material.stencil().function().equals("Equal"))
                type=NativePortalRenderTypes.overlay(type,draw.texture);
            if(!outlineOnly)geometry(draw,poses,collector,type,rgba,drawLight,drawOverlay);
            if(!outlineOnly&&draw.material.glint())geometry(draw,poses,collector,RenderTypes.armorEntityGlint(),-1,drawLight,drawOverlay);
            if(outline!=0)geometry(draw,poses,collector,RenderTypes.outline(draw.texture),outline,drawLight,drawOverlay);
        }
        if(outlineOnly)return;
        NativeEntityEquipment.submit(frame.equipment,equipmentRenderer,poses,collector,light,outline);
        for(var attachment:frame.attachments) {
            poses.pushPose();
            poses.mulPose(attachment.transform);
            int itemLight=attachment.fullBright?net.minecraft.util.LightCoordsUtil.FULL_BRIGHT:light;
            if(attachment.block!=null)attachment.block.submit(poses,collector,itemLight,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,outline);
            else attachment.item.submit(poses,collector,itemLight,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,outline);
            poses.popPose();
        }
        }finally{poses.popPose();}
    }
    private static void geometry(Draw draw,PoseStack poses,SubmitNodeCollector collector,
            net.minecraft.client.renderer.rendertype.RenderType type,int color,int light,int overlay) {
            var appearance=draw.appearance;
            collector.submitCustomGeometry(poses,type,(pose,consumer)-> {
                // Model-to-renderer conversion reflects Y, so reverse winding
                // once here to keep culled entity and held-item faces outward.
                for(var quad:draw.mesh)for(int i=0;i<quad.vertices().size();i++) {
                    var vertex=quad.vertices().get(BedrockModelSpace.rendererVertexIndex(i,quad.vertices().size()));
                    var normal=vertex.normal();
                    consumer.addVertex(pose,vertex.x()/16f,1.5f-vertex.y()/16f,vertex.z()/16f)
                        .setColor(color).setUv((float)(vertex.u()*appearance.scaleU()+appearance.offsetU()),
                            (float)(vertex.v()*appearance.scaleV()+appearance.offsetV())).setOverlay(overlay).setLight(light)
                        .setNormal(pose,normal==null?quad.nx():(float)normal.x(),
                            normal==null?-quad.ny():-(float)normal.y(),normal==null?quad.nz():(float)normal.z());
                }
            });
    }
    private static void attach(List<Attachment> output,org.joml.Matrix4f anchor,net.minecraft.client.renderer.item.ItemStackRenderState item,double scale) {
        if(anchor==null||item.isEmpty())return;
        var matrix=dev.matanyah.bedrockcompat.bedrock.entity.BedrockModelSpace.rendererTransform().mul(anchor).scale(-16,16,16)
            .rotateX((float)-Math.PI/2).rotateY((float)Math.PI).scale((float)scale);
        output.add(new Attachment(matrix,item));
    }
    private static int channel(int base,double multiplier){return (int)Math.max(0,Math.min(255,Math.round(base*multiplier)));}
}
