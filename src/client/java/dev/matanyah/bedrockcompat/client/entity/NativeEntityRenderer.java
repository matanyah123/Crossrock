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
    public record Attachment(org.joml.Matrix4fc transform,net.minecraft.client.renderer.item.ItemStackRenderState item) {}
    public record Frame(List<Draw> draws,List<Attachment> attachments,float yaw,float pitch,BedrockAnimation.Vector scale) {}
    private record Instance(BedrockEntityInstance runtime, double bornAt) {}
    public enum View { WORLD, FIRST_PERSON, UI }
    private static final Map<Entity,EnumMap<View,Instance>> instances = new WeakHashMap<>();
    private static NativeEntityResources.Snapshot generation;
    private NativeEntityRenderer() {}

    public static Frame extract(Entity entity, EntityRenderState state, float partialTick, Identifier javaTexture) {
        return extract(entity,state,partialTick,javaTexture,View.WORLD);
    }
    public static Frame extract(Entity entity, EntityRenderState state, float partialTick, Identifier javaTexture,View view) {
        var resources = NativeEntityResources.snapshot();
        if (generation != resources) { instances.clear(); generation = resources; }
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        var definition = resources.entities().get(type);
        if(definition==null)definition=resources.entities().get(BedrockEntityIds.bedrock(type));
        if (definition == null) return null;
        try {
            double now = (entity.tickCount + partialTick) / 20.0;
            final var resolvedDefinition=definition;
            Map<String,Object> facts = new HashMap<>();
            facts.put("owner_identifier",resolvedDefinition.identifier());
            facts.put("pack_settings",resources.packSettings().getOrDefault(resolvedDefinition.identifier(),Map.of()));
            facts.put("life_time",now); facts.put("delta_time",Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks()/20.0);
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
                facts.put("health",(double)living.getHealth());facts.put("max_health",(double)living.getMaxHealth());
                facts.put("hurt_direction",(double)living.getHurtDir());facts.put("hurt_time",living.hurtTime/20d);
                facts.put("body_y_rotation",(double)livingState.bodyRot);facts.put("head_x_rotation",(double)livingState.xRot);
                facts.put("head_y_rotation",(double)livingState.yRot);
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
            if(!resources.priority().owns(javaTexture.toString(),EntityModelNames.candidates(type,baby,facts)))return null;
            var instance = instances.computeIfAbsent(entity,key -> new EnumMap<>(View.class)).computeIfAbsent(view,key -> new Instance(new BedrockEntityInstance(resolvedDefinition,
                entity.getUUID().getMostSignificantBits()^entity.getUUID().getLeastSignificantBits(),
                message->NativeEntityResources.runtimeError(type+": "+message)),now));
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
            var context = new EntityQueryContext(entity.getName().getString(),biome.unwrapKey().map(k->k.identifier().toString()).orElse(""),tags,facts);
            Map<String,EntityFrameQueries.Item> equipment = new HashMap<>();
            Map<String,EquipmentSlot> slots=Map.of("slot.weapon.mainhand",EquipmentSlot.MAINHAND,"slot.weapon.offhand",EquipmentSlot.OFFHAND,
                "slot.armor.head",EquipmentSlot.HEAD,"slot.armor.chest",EquipmentSlot.CHEST,"slot.armor.legs",EquipmentSlot.LEGS,"slot.armor.feet",EquipmentSlot.FEET);
            if(entity instanceof LivingEntity living)slots.forEach((name,slot)-> {
                var stack=living.getItemBySlot(slot);Set<String> itemTags=new HashSet<>();
                stack.typeHolder().tags().forEach(tag->itemTags.add(tag.location().toString()));
                equipment.put(name,new EntityFrameQueries.Item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),itemTags));
            });
            var camera=Minecraft.getInstance().gameRenderer.mainCamera();
            String graphics=Minecraft.getInstance().options.graphicsPreset().get()==net.minecraft.client.GraphicsPreset.FAST?"simple":"fancy";
            Molang.Queries frameQueries = new EntityEnvironmentQueries(
                new EntityFrameQueries(context,equipment,camera.xRot(),camera.yRot(),graphics),(x,y,z,tag)-> {
                    var pos=new net.minecraft.core.BlockPos(x,y,z);
                    var id=Identifier.tryParse(tag);
                    // Observational only: resource-pack expressions must never load chunks.
                    return id!=null&&entity.level().hasChunkAt(pos)&&entity.level().getBlockState(pos).is(
                        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,id));
                },entity.getX(),entity.getY(),entity.getZ());
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
                resources.animationControllers(),pose,Math.max(0,now-instance.bornAt),resources.geometries(),engineVariables,runtimeAliases);
            boolean localFirstPerson=entity==Minecraft.getInstance().player&&Minecraft.getInstance().options.getCameraType().isFirstPerson();
            boolean audible=view==View.FIRST_PERSON?localFirstPerson:view==View.WORLD&&!localFirstPerson;
            for(var effect:audible?frame.effects():List.<BedrockEntityEffect>of()) {
                if(effect.kind()==BedrockEntityEffect.Kind.SOUND) {
                    var id=NativeSoundEvents.id(effect.effect());
                    var manager=Minecraft.getInstance().getSoundManager();
                    if(manager.getSoundEvent(id)==null)NativeEntityResources.runtimeError(type+": Missing sound event: "+effect.effect());
                    else manager.play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                        net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id),net.minecraft.sounds.SoundSource.NEUTRAL,
                        1,1,entity.getRandom(),entity.getX(),entity.getY(),entity.getZ()));
                } else NativeEntityResources.runtimeError(type+": Native particle emitter not yet supported: "+effect.effect());
            }
            List<Draw> draws = new ArrayList<>();
            List<Attachment> attachments=new ArrayList<>();boolean attached=false;
            for(var selection:frame.selections()) {
                var geometry=resources.geometries().get(selection.geometry());
                if(geometry==null)throw new IllegalArgumentException("Missing geometry: "+selection.geometry());
                var mesh=BedrockGeometryMesh.bake(geometry,frame.poses(),selection.visibility());
                if(!attached&&state instanceof net.minecraft.client.renderer.entity.state.ArmedEntityRenderState armed) {
                    var anchors=BedrockGeometryMesh.anchors(geometry,frame.poses());
                    attach(attachments,anchors.getOrDefault("rightitem",anchors.get("rightItem")),armed.rightHandItemState,frame.heldItemScale());
                    attach(attachments,anchors.getOrDefault("leftitem",anchors.get("leftItem")),armed.leftHandItemState,frame.heldItemScale());
                    attached=!attachments.isEmpty();
                }
                List<Identifier> selectedTextures=new ArrayList<>();
                for(String texture:selection.textures()) {
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
                    List<Identifier> drawTextures=selectedTextures;
                    if(resolved.masked()) {
                        if(selectedTextures.size()!=2)throw new IllegalArgumentException("Masked material requires exactly two textures: "+material);
                        drawTextures=List.of(NativeEntityTextures.masked(selectedTextures.get(0),selectedTextures.get(1)));
                    }
                    var quadsSnapshot=List.copyOf(quads);
                    for(var drawTexture:drawTextures)draws.add(new Draw(drawTexture,resolved,quadsSnapshot,selection.appearance()));
                });
            }
            // A valid render controller may deliberately hide every bone, or its condition
            // may select no layer this frame. An empty native frame is not a missing model:
            // falling back here would incorrectly flash the vanilla entity into view.
            if(draws.isEmpty()&&resolvedDefinition.controllers().stream().noneMatch(c->resources.renderControllers().containsKey(c.identifier())))return null;
            return new Frame(List.copyOf(draws),List.copyOf(attachments),BedrockEntityIds.bodyYaw(type,entity.getYRot(partialTick)),
                boat.isPresent()?0:entity.getXRot(partialTick),frame.scale());
        } catch(RuntimeException exception) {
            NativeEntityResources.runtimeError(type+": "+exception.getMessage());
            return null;
        }
    }

    public static void submit(Frame frame, PoseStack poses, SubmitNodeCollector collector, int light,int overlay,int color,int outline) {
        submit(frame,poses,collector,light,overlay,color,outline,false);
    }
    public static void submit(Frame frame, PoseStack poses, SubmitNodeCollector collector, int light,int overlay,int color,int outline,boolean outlineOnly) {
        poses.pushPose();
        try {
        poses.translate(0,1.5,0);poses.scale((float)frame.scale.x(),(float)frame.scale.y(),(float)frame.scale.z());poses.translate(0,-1.5,0);
        for(var draw:frame.draws) {
            var appearance=draw.appearance;var tint=appearance.color();
            int rgba=channel(color>>>24,tint.a())<<24|channel(color>>>16&255,tint.r())<<16
                |channel(color>>>8&255,tint.g())<<8|channel(color&255,tint.b());
            int drawLight=appearance.fullBright()||draw.material.emissive()?net.minecraft.util.LightCoordsUtil.FULL_BRIGHT:light;
            int drawOverlay=appearance.replaceHurtOverlay()?net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY:overlay;
            var type=draw.material.additive()?RenderTypes.eyes(draw.texture):draw.material.emissive()?RenderTypes.entityTranslucentEmissive(draw.texture)
                :draw.material.blend()||(rgba>>>24)<255?RenderTypes.entityTranslucent(draw.texture):draw.material.cull()?RenderTypes.entityCutoutCull(draw.texture):RenderTypes.entityCutout(draw.texture);
            if(!outlineOnly)geometry(draw,poses,collector,type,rgba,drawLight,drawOverlay);
            if(outline!=0)geometry(draw,poses,collector,RenderTypes.outline(draw.texture),outline,drawLight,drawOverlay);
        }
        if(outlineOnly)return;
        for(var attachment:frame.attachments) {
            poses.pushPose();
            poses.mulPose(attachment.transform);
            attachment.item.submit(poses,collector,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,outline);
            poses.popPose();
        }
        }finally{poses.popPose();}
    }
    private static void geometry(Draw draw,PoseStack poses,SubmitNodeCollector collector,
            net.minecraft.client.renderer.rendertype.RenderType type,int color,int light,int overlay) {
            var appearance=draw.appearance;
            collector.submitCustomGeometry(poses,type,(pose,consumer)-> {
                for(var quad:draw.mesh)for(var vertex:quad.vertices()) {
                    var normal=vertex.normal();
                    consumer.addVertex(pose,-vertex.x()/16f,1.5f-vertex.y()/16f,vertex.z()/16f)
                        .setColor(color).setUv((float)(vertex.u()*appearance.scaleU()+appearance.offsetU()),
                            (float)(vertex.v()*appearance.scaleV()+appearance.offsetV())).setOverlay(overlay).setLight(light)
                        .setNormal(pose,normal==null?-quad.nx():-(float)normal.x(),
                            normal==null?-quad.ny():-(float)normal.y(),normal==null?quad.nz():(float)normal.z());
                }
            });
    }
    private static void attach(List<Attachment> output,org.joml.Matrix4f anchor,net.minecraft.client.renderer.item.ItemStackRenderState item,double scale) {
        if(anchor==null||item.isEmpty())return;
        var matrix=new org.joml.Matrix4f().translate(0,1.5f,0).scale(-1/16f,-1/16f,1/16f).mul(anchor).scale(16)
            .rotateX((float)-Math.PI/2).rotateY((float)Math.PI).scale((float)scale);
        output.add(new Attachment(matrix,item));
    }
    private static int channel(int base,double multiplier){return (int)Math.max(0,Math.min(255,Math.round(base*multiplier)));}
}
