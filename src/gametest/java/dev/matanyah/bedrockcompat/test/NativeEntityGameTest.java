package dev.matanyah.bedrockcompat.test;

import dev.matanyah.bedrockcompat.client.BedrockOptionsState;
import dev.matanyah.bedrockcompat.client.entity.*;
import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackProvider;
import dev.matanyah.bedrockcompat.entity.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.Rotations;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;

/** Exercises real resource generation, renderer extraction, server arms, pose sync and removal. */
public final class NativeEntityGameTest implements FabricClientGameTest {
    private static final String PACK="c11daf7e-aef0-4ed9-bc85-131df99f3c73";
    private static final List<String> CAT_COATS=List.of("white","black","red","siamese","british_shorthair","calico","persian","ragdoll","tabby","all_black","jellie");
    @Override public void runTest(ClientGameTestContext context) {
        try {
            if(System.getenv("CROSSROCK_BEHAVIOR_ONLY")!=null){BehaviorInfrastructureChecks.run(context);return;}
            if(System.getenv("BEDROCK_REFERENCE_MATERIALS_ONLY")!=null) {
                reference(context,Path.of(System.getenv("BEDROCK_REFERENCE_PACK")));return;
            }
            PackConfigScreenChecks.run(context);
            Path pack=context.computeOnClient(mc->mc.gameDirectory.toPath().resolve("addons/native-entity-gametest"));
            fixture(pack);
            PresentationResourceChecks.fixture(pack);
            ParticleChecks.fixture(pack);
            BiomeVariantChecks.fixture(pack);
            EnvironmentChecks.fixture(context,pack);
            ClientEntityChecks.fixture(pack);
            BlockItemAssetChecks.fixture(pack);
            StaticBlockGeometryChecks.fixture(pack);
            BlockAssetExpansionChecks.fixture(pack);
            JsonUiChecks.fixture(pack);
            context.runOnClient(mc->BedrockOptionsState.setSelectedIds(List.of(PACK)));
            reload(context,true);
            ResourcePackTabsChecks.run(context);
            if(System.getenv("BEDROCK_UX_ONLY")!=null)return;
            PresentationResourceChecks.verify(context);
            check(NativeEntityResources.affectsArmorStand(),"Pack did not activate armor-stand compatibility");
            try(var world=context.worldBuilder().create()) {
                var server=world.getServer();
                server.runCommand("gamemode creative @a");
                server.runCommand("fill -5 98 -5 5 98 5 minecraft:stone");
                server.runCommand("execute as @a run tp @s 0 99 4 180 0");
                server.runCommand("summon minecraft:armor_stand 0 99 0 {NoGravity:1b}");
                server.waitFor(s->stand(s)!=null&&((BedrockArmorStandAccess)stand(s)).bedrockCompatState().active());
                server.runOnServer(ArmorStandPersistenceChecks::run);
                UUID id=server.computeOnServer(s->stand(s).getUUID());
                server.runOnServer(s-> {
                    ArmorStand stand=stand(s);
                    check(stand.showArms(),"Server did not enable arms");
                    stand.setHeadPose(new Rotations(12,34,56));
                    var player=s.getPlayerList().getPlayers().getFirst();
                    player.setShiftKeyDown(true);
                    stand.interact(player,InteractionHand.MAIN_HAND,new Vec3(0,1,0));
                    player.setShiftKeyDown(false);
                    check(((BedrockArmorStandAccess)stand).bedrockCompatState().poseIndex()==1,"Sneak interaction did not cycle pose");
                    check(stand.getHeadPose().equals(new Rotations(12,34,56)),"Bedrock pose destroyed Java head pose");
                });
                context.waitFor(mc->mc.level!=null&&mc.level.getEntity(id) instanceof ArmorStand stand
                    &&((BedrockArmorStandAccess)stand).bedrockCompatState().poseIndex()==1);
                server.runCommand("data merge entity @e[type=minecraft:armor_stand,limit=1] {\"bedrock_compat:pose_index\":5}");
                context.waitFor(mc->mc.level.getEntity(id) instanceof ArmorStand stand
                    &&((BedrockArmorStandAccess)stand).bedrockCompatState().poseIndex()==5);
                context.runOnClient(mc-> {
                    var stand=(ArmorStand)mc.level.getEntity(id);
                    var renderer=mc.getEntityRenderDispatcher().getRenderer(stand);
                    var state=renderer.createRenderState(stand,0);
                    var frame=((NativeRenderState)state).bedrockCompatFrame();
                    check(frame!=null&&!frame.draws().isEmpty(),"Native renderer produced no mesh: "+NativeEntityResources.errors());
                    var oldName=stand.getCustomName();
                    try {
                        stand.setCustomName(net.minecraft.network.chat.Component.literal("emissive_fixture"));
                        // Use a fresh view runtime: same-timestamp world extraction intentionally
                        // reuses its snapshot rather than running pre-animation twice.
                        var glow=NativeEntityRenderer.extract(stand,state,0,frame.draws().getFirst().texture(),NativeEntityRenderer.View.UI);
                        check(glow!=null&&!glow.draws().isEmpty(),"Emissive fixture failed extraction");
                        var draw=glow.draws().getFirst();
                        check(draw.material().alphaTest()&&!draw.material().blend(),"Emissive cutout became translucent");
                        check(draw.texture().getNamespace().equals("bedrock_native"),"Emissive light mask must reach the shader unmodified");
                        try(var stream=mc.getResourceManager().open(draw.texture());var image=com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                            check(image.getPixel(0,0)==0x01ffffff,"Emissive light mask alpha was lost");
                            check(image.getPixel(1,0)==0,"Emissive cutout filled its transparent background");
                        }catch(java.io.IOException exception){throw new AssertionError(exception);}
                    } finally {stand.setCustomName(oldName);}
                });
                world.getConnection().waitForChunksRender();
                NativeQueryChecks.verify(context);
                ClientEntityChecks.verify(context,server::runCommand);
                JsonUiChecks.verify(context,server::runCommand);
                JsonUiInteractionChecks.verify(context,action->server.runOnServer(action::accept),server::runCommand);
                EnvironmentChecks.verify(context,server::runCommand);
                BlockItemAssetChecks.verify(context);
                ParticleChecks.verify(context);
                server.runCommand("fillbiome -4 96 -4 4 104 4 minecraft:desert");
                server.runCommand("summon minecraft:rabbit 2 99 0 {NoAI:1b,Silent:1b}");
                context.waitFor(mc->mc.level!=null&&java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false)
                    .anyMatch(e->e instanceof net.minecraft.world.entity.animal.rabbit.Rabbit));
                BiomeVariantChecks.verify(context,"textures/entity/emissive_fixture.png");
                server.runCommand("fillbiome -4 96 -4 4 104 4 minecraft:forest");
                BiomeVariantChecks.verify(context,"textures/entity/armor_stand.png");
                server.runCommand("kill @e[type=minecraft:rabbit]");
                context.takeScreenshot("native-armor-stand");
                server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] armor.head with minecraft:diamond_helmet");
                server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] armor.chest with minecraft:diamond_chestplate");
                server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] armor.legs with minecraft:diamond_leggings");
                server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] armor.feet with minecraft:diamond_boots");
                server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] weapon.mainhand with minecraft:diamond_sword");
                server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] weapon.offhand with minecraft:shield");
                context.waitFor(mc-> {
                    var stand=(ArmorStand)mc.level.getEntity(id);
                    return !stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()
                        &&!stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
                        &&!stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty()
                        &&!stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty()
                        &&!stand.getMainHandItem().isEmpty()&&!stand.getOffhandItem().isEmpty();
                });
                context.runOnClient(mc-> {
                    var stand=mc.level.getEntity(id);
                    var state=mc.getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand,1);
                    var frame=((NativeRenderState)state).bedrockCompatFrame();
                    check(frame!=null&&frame.equipment().size()==9,"Not all armor-slot parts reached native equipment rendering: "+NativeEntityResources.errors());
                    check(frame.attachments().size()==2,"Arm-bone fallback did not render both held items");
                });
                context.waitTicks(3);context.takeScreenshot("native-armor-stand-full-equipment");
                server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] weapon.mainhand with minecraft:iron_sword");
                context.waitFor(mc->((ArmorStand)mc.level.getEntity(id)).getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD));
                var propMesh=new java.util.concurrent.atomic.AtomicReference<List<dev.matanyah.bedrockcompat.bedrock.entity.BedrockGeometryMesh.Quad>>();
                context.runOnClient(mc-> {
                    var stand=mc.level.getEntity(id);
                    var frame=((NativeRenderState)mc.getEntityRenderDispatcher().getRenderer(stand)
                        .createRenderState(stand,1)).bedrockCompatFrame();
                    check(frame!=null&&frame.draws().stream().flatMap(draw->draw.mesh().stream()).anyMatch(quad->quad.bone().equals("fixture_prop")),
                        "Bound native held-attachable did not reach the renderer: "+NativeEntityResources.errors());
                    check(frame.attachments().size()==1,"Native held-attachable duplicated its Java held-item model");
                    propMesh.set(frame.draws().stream().flatMap(draw->draw.mesh().stream())
                        .filter(quad->quad.bone().equals("fixture_prop")).toList());
                });
                context.waitTicks(8);
                context.runOnClient(mc-> {
                    var stand=mc.level.getEntity(id);
                    var frame=((NativeRenderState)mc.getEntityRenderDispatcher().getRenderer(stand)
                        .createRenderState(stand,1)).bedrockCompatFrame();
                    var moved=frame.draws().stream().flatMap(draw->draw.mesh().stream())
                        .filter(quad->quad.bone().equals("fixture_prop")).toList();
                    check(!moved.equals(propMesh.get()),"Native attachable animation did not advance");
                });
                context.takeScreenshot("native-armor-stand-bound-prop");
                server.runOnServer(s->stand(s).setCustomName(net.minecraft.network.chat.Component.literal("hidden")));
                context.waitFor(mc->mc.level.getEntity(id).getName().getString().equals("hidden"));
                context.waitTicks(2);
                context.runOnClient(mc-> {
                    var stand=mc.level.getEntity(id);
                    var state=mc.getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand,1);
                    var hidden=((NativeRenderState)state).bedrockCompatFrame();
                    check(hidden!=null&&hidden.draws().isEmpty(),"Hidden native bones fell back to the vanilla model");
                    check(hidden.equipment().isEmpty(),"Hidden native bones leaked equipped armor");
                    check(hidden.attachments().isEmpty(),"Hidden native bones leaked held items");
                });
                server.runOnServer(s->stand(s).setCustomName(null));
                context.waitFor(mc->!mc.level.getEntity(id).hasCustomName());
                server.runOnServer(s-> {
                    var stand=stand(s);
                    for(var slot:List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,
                            net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,
                            net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                            net.minecraft.world.entity.EquipmentSlot.OFFHAND))
                        stand.setItemSlot(slot,net.minecraft.world.item.ItemStack.EMPTY);
                });
                context.waitFor(mc-> {
                    var stand=(ArmorStand)mc.level.getEntity(id);
                    return List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,
                            net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET)
                        .stream().allMatch(slot->stand.getItemBySlot(slot).isEmpty())
                        &&stand.getMainHandItem().isEmpty()&&stand.getOffhandItem().isEmpty();
                });
                context.runOnClient(mc-> {
                    var stand=mc.level.getEntity(id);
                    var frame=((NativeRenderState)mc.getEntityRenderDispatcher().getRenderer(stand)
                        .createRenderState(stand,1)).bedrockCompatFrame();
                    check(frame!=null&&frame.equipment().isEmpty()&&frame.attachments().isEmpty(),
                        "Removed equipment remained in the native render frame");
                });
                server.runCommand("summon minecraft:arrow 2 99 0 {NoGravity:1b,Silent:1b}");
                context.waitFor(mc-> {
                    for(var e:mc.level.entitiesForRendering())if(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("arrow"))return true;
                    return false;
                });
                context.runOnClient(mc-> {
                    for(var e:mc.level.entitiesForRendering())if(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("arrow")) {
                        var state=mc.getEntityRenderDispatcher().getRenderer(e).createRenderState(e,0);
                        var frame=((NativeRenderState)state).bedrockCompatFrame();
                        check(frame!=null,"Non-living entity produced no native mesh");
                        check(frame.draws().stream().flatMap(d->d.mesh().stream()).flatMap(q->q.vertices().stream())
                            .anyMatch(v->v.normal()!=null),"Polygon vertex normals did not reach the native renderer");
                        check(frame.draws().stream().flatMap(d->d.mesh().stream()).anyMatch(q->q.bone().equals("inheritance_marker")),
                            "Legacy child geometry did not inherit its cross-file polygon parent");
                        check(frame.draws().stream().flatMap(d->d.mesh().stream()).noneMatch(q->q.bone().equals("hidden_marker")),
                            "Legacy neverRender geometry was emitted");
                        checkMaskedTexture(mc,frame);
                        var again=mc.getEntityRenderDispatcher().getRenderer(e).createRenderState(e,1);
                        check(((NativeRenderState)again).bedrockCompatFrame().draws().getFirst().texture().equals(frame.draws().getFirst().texture()),
                            "Masked entity texture was not reused between frames");
                    }
                });
                context.waitTicks(2);
                context.takeScreenshot("native-nonliving-entity");
                server.runCommand("summon minecraft:oak_boat -3 99 -2 {NoGravity:1b,Rotation:[0f,0f]}");
                server.runCommand("summon minecraft:birch_chest_boat 0 99 -3 {NoGravity:1b,Rotation:[0f,0f]}");
                server.runCommand("summon minecraft:bamboo_raft 3 99 -2 {NoGravity:1b,Rotation:[0f,0f]}");
                server.runCommand("summon minecraft:minecart 4 99 -2 {NoGravity:1b,Rotation:[0f,0f]}");
                server.runCommand("summon minecraft:pig -3 99 -2 {NoAI:1b,Silent:1b}");
                server.runCommand("ride @e[type=minecraft:pig,limit=1] mount @e[type=minecraft:oak_boat,limit=1]");
                context.waitFor(mc-> {
                    int boats=0;
                    for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat)boats++;
                    return boats==3;
                });
                context.waitFor(mc-> {
                    for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat&&entity.isVehicle())return true;
                    return false;
                });
                context.runOnClient(mc-> {
                    for(var entity:mc.level.entitiesForRendering()) {
                        var state=mc.getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity,1);
                        var frame=((NativeRenderState)state).bedrockCompatFrame();
                        if(entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat) {
                            String type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                            var boat=dev.matanyah.bedrockcompat.bedrock.entity.BedrockEntityIds.boat(type).orElseThrow();
                            check(frame!=null&&!frame.draws().isEmpty(),"Shared Bedrock boat failed: "+type+"; "+NativeEntityResources.errors());
                            double marker=frame.draws().stream().flatMap(d->d.mesh().stream()).filter(q->q.bone().equals("variant_marker"))
                                .flatMap(q->q.vertices().stream()).mapToDouble(v->v.x()).min().orElseThrow();
                            check(Math.abs(marker-boat.variant())<1e-4,"Boat lost its Bedrock variant: "+type);
                            check(Math.abs(frame.yaw()-90)<1e-4,"Boat facing was not adapted to Bedrock");
                            double lerp=frame.draws().stream().flatMap(d->d.mesh().stream()).filter(q->q.bone().equals("lerp_marker"))
                                .flatMap(q->q.vertices().stream()).mapToDouble(v->v.x()).min().orElseThrow();
                            check(Math.abs(lerp-.5)<1e-4,"Nested override or keyframe-local interpolation failed in the rendered boat mesh");
                            boolean rider=frame.draws().stream().flatMap(d->d.mesh().stream()).anyMatch(q->q.bone().equals("rider_marker"));
                            check(rider==entity.isVehicle(),"Non-living rider query did not match passengers");
                        }
                        if(entity instanceof net.minecraft.world.entity.animal.pig.Pig)check(frame==null,"Untouched passenger was replaced");
                    }
                });
                context.waitTicks(3);context.takeScreenshot("native-boat-variants");
                context.waitFor(mc-> {
                    for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart)return true;
                    return false;
                });
                context.runOnClient(mc-> {
                    int testedVehicles=0;
                    for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.vehicle.VehicleEntity vehicle) {
                        var state=mc.getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity,.25f);
                        // A fresh view clock isolates the input check from the world's latest
                        // render extraction: rewinding an already sampled clock is deliberately ignored.
                        String type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                        var texture=net.minecraft.resources.Identifier.parse(
                            dev.matanyah.bedrockcompat.bedrock.entity.EntityTextureNames.nonLiving(type,false));
                        int oldTime=vehicle.getHurtTime(),oldDirection=vehicle.getHurtDir();float oldDamage=vehicle.getDamage();
                        NativeEntityRenderer.Frame frame;
                        try {
                            vehicle.setHurtTime(10);vehicle.setHurtDir(-1);vehicle.setDamage(4.5f);
                            frame=NativeEntityRenderer.extract(entity,state,.25f,texture,NativeEntityRenderer.View.UI);
                        } finally {
                            vehicle.setHurtTime(oldTime);vehicle.setHurtDir(oldDirection);vehicle.setDamage(oldDamage);
                        }
                        check(frame!=null,"Vehicle lost native frame during partial-tick extraction: "+type);
                        double alpha=frame.draws().stream().flatMap(d->d.mesh().stream()).filter(q->q.bone().equals("alpha_marker"))
                            .flatMap(q->q.vertices().stream()).mapToDouble(v->v.y()).min().orElseThrow();
                        check(Math.abs(alpha-.25)<1e-4,"Non-living frame_alpha was missing or used entity age: "+alpha);
                        var damageVertices=frame.draws().stream().flatMap(d->d.mesh().stream()).filter(q->q.bone().equals("damage_marker"))
                            .flatMap(q->q.vertices().stream()).toList();
                        check(Math.abs(damageVertices.stream().mapToDouble(v->v.x()).min().orElseThrow()-9.75)<1e-4,
                            "Vehicle hurt countdown was converted to seconds or interpolated twice: "+type);
                        check(Math.abs(damageVertices.stream().mapToDouble(v->v.y()).min().orElseThrow()-35.5)<1e-4,
                            "Vehicle structural integrity did not preserve fractional damage: "+type);
                        check(Math.abs(damageVertices.stream().mapToDouble(v->v.z()).min().orElseThrow()+1)<1e-4,
                            "Vehicle hurt direction was not exposed: "+type);
                        testedVehicles++;
                    }
                    check(testedVehicles==4,"Vehicle input regression did not exercise all boats and minecart");
                });
                for(String coat:CAT_COATS)server.runCommand("summon minecraft:cat 0 99 -4 {NoAI:1b,NoGravity:1b,Silent:1b,variant:\"minecraft:"+coat+"\"}");
                context.waitFor(mc-> {
                    int cats=0;for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.animal.feline.Cat)cats++;
                    return cats==CAT_COATS.size();
                });
                checkCats(context,false);
                server.runOnServer(s-> {
                    for(var level:s.getAllLevels())for(var entity:level.getAllEntities())if(entity instanceof net.minecraft.world.entity.animal.feline.Cat cat) {
                        cat.setTame(true,false);cat.setOrderedToSit(true);cat.setInSittingPose(true);
                        cat.setLying(true);
                    }
                });
                context.waitFor(mc-> {
                    int cats=0;
                    for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.animal.feline.Cat cat) {
                        if(!cat.isInSittingPose()||!cat.isLying())return false;
                        cats++;
                    }
                    return cats==CAT_COATS.size();
                });
                context.waitTicks(2);checkCats(context,true);
                server.runOnServer(s-> {
                    for(var level:s.getAllLevels())for(var entity:level.getAllEntities())if(entity instanceof net.minecraft.world.entity.animal.feline.Cat cat) {
                        cat.setOrderedToSit(false);cat.setInSittingPose(false);cat.setLying(false);
                    }
                });
                context.waitFor(mc-> {
                    for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.animal.feline.Cat cat
                        &&(cat.isInSittingPose()||cat.isLying()))return false;
                    return true;
                });
                context.waitTicks(2);checkCats(context,false);
                server.runCommand("kill @e[type=minecraft:cat]");
                context.runOnClient(mc-> {
                    var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
                    var nativePlayer=((NativeRenderState)state).bedrockCompatFrame();
                    check(nativePlayer!=null&&!nativePlayer.draws().isEmpty(),"Player fixture did not render: "+NativeEntityResources.errors());
                    check(nativePlayer.draws().getFirst().texture().equals(mc.player.getSkin().body().texturePath()),"Actual player skin was not bound");
                    check(nativePlayer.scale().x()==1,"Player animation did not observe the block beneath it");
                    var previousHelmet=mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).copy();
                    try {
                        mc.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
                        var equippedState=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
                        var equippedFrame=((NativeRenderState)equippedState).bedrockCompatFrame();
                        check(equippedFrame!=null&&!equippedFrame.equipment().isEmpty(),"Native player lost equipped armor");
                    } finally {mc.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,previousHelmet);}
                    var hands=NativeEntityRenderer.extract(mc.player,state,1,mc.player.getSkin().body().texturePath(),NativeEntityRenderer.View.FIRST_PERSON);
                    check(hands!=null&&!hands.draws().isEmpty(),"First-person player controller failed");
                    check(hands.draws().stream().flatMap(d->d.mesh().stream()).allMatch(q->q.bone().contains("arm")||q.bone().contains("sleeve")),"First-person pass leaked body geometry");
                });
                context.takeScreenshot("native-player-first-person");
                var registration=NativePlayerAnimationApi.register((player,partial,view,variables,queries)->variables.put("fixture_shape",1d));
                try {
                    context.waitTicks(2);
                    context.runOnClient(mc-> {
                        var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
                        var frame=((NativeRenderState)state).bedrockCompatFrame();
                        check(frame!=null&&frame.draws().stream().flatMap(d->d.mesh().stream()).anyMatch(q->q.bone().equals("custom_root"))
                            &&frame.draws().stream().flatMap(d->d.mesh().stream()).allMatch(q->Set.of("custom_root","head").contains(q.bone())),"Provider-driven pack morph did not select alternate geometry");
                    });
                }finally{registration.close();}
                context.waitTicks(2);
                context.runOnClient(mc-> {
                    var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
                    var frame=((NativeRenderState)state).bedrockCompatFrame();
                    check(frame!=null&&frame.draws().stream().flatMap(d->d.mesh().stream()).anyMatch(q->q.bone().equals("rightarm")),
                        "Removing provider left the player stuck in the morph");
                });
                // The front camera at z=0 otherwise sits inside the stand we are testing.
                server.runCommand("execute as @a run tp @s -2 99 4 180 0");
                context.waitFor(mc->mc.player.getX()<-1);
                context.runOnClient(mc->mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
                context.waitTicks(4);context.takeScreenshot("native-player-third-person");
                context.runOnClient(mc->mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
                server.runCommand("execute as @a run tp @s 0 99 4 180 0");
                StaticBlockGeometryChecks.verify(context,server::runCommand);
                StaticBlockGeometryChecks.verifyReload(context,pack,()->reload(context,true));
                BlockAssetExpansionChecks.verify(context,server::runCommand);
                priority(context,id,pack);
                var checkUiReload=JsonUiInteractionChecks.prepareReload(context,action->server.runOnServer(action::accept),server::runCommand);
                context.runOnClient(mc->BedrockOptionsState.setSelectedIds(List.of()));
                reload(context,false);
                checkUiReload.run();
                context.waitTicks(4);
                JsonUiChecks.cleared(context);
                JsonUiInteractionChecks.fallback(context,action->server.runOnServer(action::accept),server::runCommand,"vanilla");
                EnvironmentChecks.disabled(context);
                BlockItemAssetChecks.disabled(context);
                StaticBlockGeometryChecks.disabled(context);
                BlockAssetExpansionChecks.disabled(context);
                server.waitFor(s->!((BedrockArmorStandAccess)stand(s)).bedrockCompatState().active());
                server.runOnServer(s-> {
                    var stand=stand(s);
                    check(!stand.showArms(),"Removing pack did not restore original arms");
                    check(stand.getHeadPose().equals(new Rotations(12,34,56)),"Removing pack destroyed Java pose");
                });
                context.runOnClient(mc-> {
                    var stand=(ArmorStand)mc.level.getEntity(id);
                    var state=mc.getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand,0);
                    check(((NativeRenderState)state).bedrockCompatFrame()==null,"Native mesh remained after pack removal");
                    var playerState=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
                    check(((NativeRenderState)playerState).bedrockCompatFrame()==null,"Removing player pack did not restore vanilla rendering");
                });
            }
            context.runOnClient(mc-> {
                check(net.minecraft.util.StringUtil.isAllowedChatCharacter(0xA7),"Formatting character still rejected");
                check("§aName".equals(net.minecraft.util.StringUtil.filterText("§aName")),"Pasted formatting was stripped");
                check(!net.minecraft.util.StringUtil.isAllowedChatCharacter(0),"Control characters must stay blocked");
                var input=new net.minecraft.client.gui.components.EditBox(mc.font,0,0,100,20,
                    net.minecraft.network.chat.Component.literal("Formatting test"));
                input.setFocused(true);input.charTyped(new net.minecraft.client.input.CharacterEvent(0xA7));
                input.insertText("aName");check("§aName".equals(input.getValue()),"Text field did not accept typed formatting");
            });
            String reference=System.getenv("BEDROCK_REFERENCE_PACK");
            if(reference!=null&&!reference.isBlank())reference(context,Path.of(reference));
            String uiReference=System.getenv("BEDROCK_UI_REFERENCE_PACK");
            if(uiReference!=null&&!uiReference.isBlank())uiReference(context,Path.of(uiReference));
        } catch(Exception exception) { throw new RuntimeException(exception); }
    }
    private static void uiReference(ClientGameTestContext context,Path source)throws Exception {
        Path game=context.computeOnClient(mc->mc.gameDirectory.toPath());Path copy=game.resolve("addons/ui-reference.mcpack");
        Files.createDirectories(copy.getParent());Files.copy(source,copy,StandardCopyOption.REPLACE_EXISTING);
        var pack=new dev.matanyah.bedrockcompat.bedrock.discovery.BedrockPackRepository(game).scan().stream().filter(p->p.source().equals(copy)).flatMap(p->p.pack().stream()).findFirst().orElseThrow();
        context.runOnClient(mc->BedrockOptionsState.setSelectedIds(List.of(pack.uuid())));reload(context,true);
        context.runOnClient(mc->{
            var trace=dev.matanyah.bedrockcompat.client.ui.json.NativeJsonUi.dependencies();
            if(trace.definitions().stream().noneMatch(d->d.id().equals("pagination._ui_screen_generator")&&d.file().equals("update-logs.txt")))throw new AssertionError("Registered root-relative UI document was not loaded");
            if(trace.definitions().stream().noneMatch(d->d.id().equals("chest.small_chest_screen")))throw new AssertionError("Implicit chest patch missing");
        });
        try(var world=context.worldBuilder().create()){var server=world.getServer();JsonUiInteractionChecks.fallback(context,action->server.runOnServer(action::accept),server::runCommand,"real-pack");}
        if(Files.mismatch(source,copy)!=-1)throw new AssertionError("Read-only reference archive changed");
        context.runOnClient(mc->BedrockOptionsState.setSelectedIds(List.of()));reload(context,false);
    }
    private static void referenceMaterials(ClientGameTestContext context) throws Exception {
        context.runOnClient(mc->{
            for(int i=0;i<16;i++)try(var stream=mc.getResourceManager().getResourceOrThrow(
                    net.minecraft.resources.Identifier.parse("minecraft:textures/particle/explosion_"+i+".png")).open()) {
                var sprite=ImageIO.read(stream);
                check(sprite.getWidth()==8&&sprite.getHeight()==8,"Explosion did not use the imported pack frame "+i);
            }catch(java.io.IOException ex){throw new AssertionError(ex);}
        });
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            PlayerBalanceChecks.verify(context,server::runCommand);
            context.runOnClient(mc->mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
            server.runCommand("gamemode creative @a");
            server.runCommand("fill -5 98 -5 5 98 5 minecraft:stone");
            server.runCommand("time set midnight");
            server.runCommand("tp @a 0 99 4 facing 0 99.8 0");
            HatAndItemPhysicsChecks.verify(context,server::runCommand);
            for(String item:List.of("armor_stand","pufferfish_bucket","wooden_sword","bell","diamond_sword[minecraft:enchantments={sharpness:1}]")) {
                server.runCommand("item replace entity @a weapon.mainhand with minecraft:"+item);
                context.waitTicks(30);
                if(item.startsWith("diamond_sword"))context.runOnClient(mc->{
                    var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
                    var hands=NativeEntityRenderer.extract(mc.player,state,1,mc.player.getSkin().body().texturePath(),NativeEntityRenderer.View.FIRST_PERSON);
                    check(mc.player.getMainHandItem().hasFoil(),"Test weapon did not receive its enchantment");
                    check(hands!=null&&hands.draws().stream().anyMatch(d->d.material().glint()),"Enchanted native weapon lost its glint pass");
                });
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("held-front-faces-"+item.split("\\[")[0]);
            }
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:allay_spawn_egg");
            for(String type:List.of("slime","wither","allay","warden","sulfur_cube")) {
                server.runCommand("kill @e[type=!minecraft:player]");
                server.runCommand("summon minecraft:"+type+" 0 99 0 {NoAI:1b,Silent:1b,NoGravity:1b}");
                if(type.equals("wither"))server.runCommand("data merge entity @e[type=minecraft:wither,limit=1] {Invul:220}");
                if(type.equals("sulfur_cube")) {
                    server.runCommand("data merge entity @e[type=minecraft:sulfur_cube,limit=1] {Size:1,Age:0}");
                    server.runCommand("attribute @e[type=minecraft:sulfur_cube,limit=1] minecraft:max_health base set 8");
                }
                server.runCommand("tp @a 0 99 4 facing 0 "+(type.equals("warden")?101:99.8)+" 0");
                context.waitTicks(40);
                world.getConnection().waitForChunksRender();
                context.runOnClient(mc->{
                    for(var e:mc.level.entitiesForRendering())if(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals(type)) {
                        var state=mc.getEntityRenderDispatcher().getRenderer(e).createRenderState(e,1);
                        var frame=((NativeRenderState)state).bedrockCompatFrame();
                        check(frame!=null&&!frame.draws().isEmpty(),type+" fell back to vanilla rendering");
                        if(type.equals("slime"))check(frame.scale().x()>0,"Slime collapsed to zero size");
                        if(type.equals("warden"))check(frame.draws().stream().anyMatch(d->d.material().depthEqual()),"Warden overlay lost its equal-depth mask");
                        if(type.equals("warden"))check(frame.draws().stream().filter(d->!d.material().depthEqual()&&!d.material().blend())
                            .flatMap(d->d.mesh().stream()).noneMatch(q->q.bone().equals("42f6")||q.bone().equals("3gola6")),
                            "Unnamed Warden enabled William's geometry");
                        check(frame.draws().stream().mapToInt(d->d.mesh().size()).sum()<100000,"Glow mask geometry exploded");
                    }
                });
                context.takeScreenshot("material-night-"+type+"-allay-egg");
                if(type.equals("warden")) {
                    server.runCommand("data merge entity @e[type=minecraft:warden,limit=1] {CustomName:'William'}");
                    context.waitTicks(10);
                    context.runOnClient(mc->{
                        for(var e:mc.level.entitiesForRendering())if(e instanceof net.minecraft.world.entity.monster.warden.Warden) {
                            var state=mc.getEntityRenderDispatcher().getRenderer(e).createRenderState(e,1);
                            var frame=((NativeRenderState)state).bedrockCompatFrame();
                            check(frame!=null&&frame.draws().stream().filter(d->!d.material().depthEqual()&&!d.material().blend()).flatMap(d->d.mesh().stream())
                                .anyMatch(q->q.bone().equals("42f6")||q.bone().equals("3gola6")),"Named William variant no longer works");
                        }
                    });
                    context.takeScreenshot("material-night-warden-William");
                }
                if(type.equals("sulfur_cube"))SulfurCubeChecks.verify(context,server::runCommand);
                if(System.getenv("BEDROCK_REFERENCE_TRAILS")!=null) {
                    if(type.equals("sulfur_cube"))server.runCommand("item replace entity @e[type=minecraft:sulfur_cube,limit=1] armor.body with minecraft:magma_block");
                    context.waitTicks(6);
                    server.runCommand("tp @a 1 100 4 facing 1 99.5 0");
                    for(int i=0;i<20;i++) {
                        server.runCommand("tp @e[type=minecraft:"+type+"] "+(i*.12)+" 99 0");
                        context.waitTicks(1);
                    }
                    // Server teleport packets and rendered locator/query snapshots are asynchronous.
                    context.waitTicks(3);
                    context.takeScreenshot("material-trail-"+type);
                    context.runOnClient(mc->{
                        System.out.println("TRAIL CHECK "+type+" particles="+dev.matanyah.bedrockcompat.client.particle.NativeParticles.particleCount()+" emitters="+dev.matanyah.bedrockcompat.client.particle.NativeParticles.emitterCount());
                        if(type.equals("sulfur_cube"))check(dev.matanyah.bedrockcompat.client.particle.NativeParticles.emitterCount()>=3,
                            "Sulfur's chained ground emitters failed");
                        try {
                            var field=dev.matanyah.bedrockcompat.client.particle.NativeParticles.class.getDeclaredField("emitters");field.setAccessible(true);
                            for(var emitter:(java.util.List<?>)field.get(null)) {
                                var df=emitter.getClass().getField("definition");df.setAccessible(true);
                                var cf=emitter.getClass().getField("context");cf.setAccessible(true);
                                if(((dev.matanyah.bedrockcompat.bedrock.entity.BedrockParticleEffect)df.get(emitter)).id.endsWith(":tvbebm")) {
                                    var values=(dev.matanyah.bedrockcompat.bedrock.entity.Molang.Context)cf.get(emitter);
                                    check(dev.matanyah.bedrockcompat.bedrock.entity.Molang.number(values.variable("jcalav"))>.49,
                                        "Entity variables reset the trail's colour");
                                    check(dev.matanyah.bedrockcompat.bedrock.entity.Molang.number(values.variable("bhblov"))>2,
                                        "Trail emitter lost its previous-position history: previous="+values.variable("bhblov")+", age="+values.variable("emitter_age"));
                                }
                            }
                        }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
                    });
                }
            }
        }
    }
    /** The source archive stays read-only; test installation lives in the isolated run directory. */
    private static void reference(ClientGameTestContext context,Path source) throws Exception {
        Path game=context.computeOnClient(mc->mc.gameDirectory.toPath());
        Path copy=game.resolve("addons/native-reference.mcpack");
        Files.createDirectories(copy.getParent());
        Files.copy(source,copy,StandardCopyOption.REPLACE_EXISTING);
        var packs=new dev.matanyah.bedrockcompat.bedrock.discovery.BedrockPackRepository(game).scan();
        var pack=packs.stream().filter(p->p.source().equals(copy)).flatMap(p->p.pack().stream()).findFirst().orElseThrow();
        context.runOnClient(mc->BedrockOptionsState.setSelectedIds(List.of(pack.uuid())));
        reload(context,true);
        if(System.getenv("BEDROCK_REFERENCE_BALANCE_ONLY")!=null) {
            try(var world=context.worldBuilder().create()) {
                PlayerBalanceChecks.verify(context,world.getServer()::runCommand);
            }
            return;
        }
        if(System.getenv("BEDROCK_REFERENCE_MATERIALS_ONLY")!=null) {
            referenceMaterials(context);return;
        }
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
            PlayerBalanceChecks.verify(context,server::runCommand);
            context.runOnClient(mc->mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
            server.runCommand("gamemode creative @a");
            server.runCommand("fill -5 98 -5 5 98 5 minecraft:stone");
            server.runCommand("execute as @a run tp @s 0 99 4 180 0");
            server.runCommand("summon minecraft:armor_stand 0 99 0 {NoGravity:1b}");
            context.waitFor(mc-> {
                if(mc.level!=null)for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand)return true;
                return false;
            });
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc-> {
                var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
                var frame=((NativeRenderState)state).bedrockCompatFrame();
                check(frame!=null&&!frame.draws().isEmpty(),"Reference player model failed: "+NativeEntityResources.errors().stream().filter(e->e.startsWith("minecraft:player:")).toList());
                var hands=NativeEntityRenderer.extract(mc.player,state,1,mc.player.getSkin().body().texturePath(),NativeEntityRenderer.View.FIRST_PERSON);
                check(hands!=null,"Reference first-person model failed: "+NativeEntityResources.errors().stream().filter(e->e.startsWith("minecraft:player:")).toList());
            });
            context.waitTicks(4);context.takeScreenshot("reference-player-first-person");
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:diamond");
            context.waitTicks(12);context.takeScreenshot("reference-held-diamond-material");
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:tropical_fish_bucket");
            server.runCommand("execute as @a run tp @s 0 115 4 180 0");
            context.waitTicks(18);
            context.runOnClient(mc-> {
                check(mc.player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT,"Falling fixture must use the right main arm");
                var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
                var hands=NativeEntityRenderer.extract(mc.player,state,1,mc.player.getSkin().body().texturePath(),NativeEntityRenderer.View.FIRST_PERSON);
                check(hands!=null&&!hands.draws().isEmpty(),"Falling first-person model failed");
            });
            context.takeScreenshot("reference-player-falling-right-hand");
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
            server.runCommand("execute as @a run tp @s -2 99 4 180 0");
            context.waitFor(mc->mc.player.getX()<-1);
            context.runOnClient(mc->mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
            context.runOnClient(mc-> {
                try(var input=new com.mojang.blaze3d.platform.NativeImage(64,64,true)) {
                    input.setPixel(42,16,0x00123456);
                    input.setPixel(2,18,0x40123456);
                    input.setPixel(8,8,0x00123456);
                    var processed=dev.matanyah.bedrockcompat.client.mixin.NativeSkinTextureInvoker.bedrockCompatProcessSkin(input,"Padding regression");
                    check(processed.getPixel(42,16)==0x00123456,"Java skin preprocessing made transparent face padding opaque");
                    check(processed.getPixel(2,18)==0x40123456,"Java skin preprocessing lost authored padding alpha");
                    check(processed.getPixel(8,8)==0xff123456,"Java skin base face transparency changed");
                }
                String skin=System.getenv("CROSSROCK_TEST_SKIN");
                if(skin!=null&&!skin.isBlank())try(var stream=java.nio.file.Files.newInputStream(java.nio.file.Path.of(skin))) {
                    mc.getTextureManager().register(mc.player.getSkin().body().texturePath(),
                        new net.minecraft.client.renderer.texture.DynamicTexture(()->"Custom skin regression",
                            dev.matanyah.bedrockcompat.client.mixin.NativeSkinTextureInvoker.bedrockCompatProcessSkin(
                                com.mojang.blaze3d.platform.NativeImage.read(stream),"Custom skin regression")));
                } catch(java.io.IOException e) {throw new IllegalStateException(e);}
            });
            context.waitTicks(4);context.takeScreenshot("reference-player-third-person");
            context.runOnClient(mc->{mc.options.fov().set(30);check(mc.options.fov().get()==30,"Close-up FOV was rejected");});
            context.waitTicks(30);context.takeScreenshot("reference-player-face-closeup");
            context.runOnClient(mc->mc.options.fov().set(70));
            server.runCommand("item replace entity @a armor.chest with minecraft:elytra");
            context.waitTicks(5);
            context.runOnClient(mc-> {
                var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
                var frame=((NativeRenderState)state).bedrockCompatFrame();
                check(frame!=null&&frame.draws().stream().anyMatch(d->d.texture().getPath().contains("elytra")),
                    "Equipped elytra has no native wing mesh: "+NativeEntityResources.errors());
            });
            context.takeScreenshot("reference-player-elytra-equipped");
            server.runCommand("gamemode survival @a");
            server.runCommand("execute as @a run tp @s -2 160 4 180 0");
            context.waitTicks(5);
            server.runOnServer(s->s.getPlayerList().getPlayers().getFirst().startFallFlying());
            context.runOnClient(mc->mc.player.startFallFlying());
            // Keep gliding input stable while the test client is not sending movement keys.
            try(var flightInputs=NativePlayerAnimationApi.register((player,partial,view,variables,queries)-> {
                if(view==NativeEntityRenderer.View.WORLD) {
                    player.startFallFlying();queries.put("is_gliding",1d);
                }
                check(((Number)queries.get("time_stamp")).longValue()==player.level().getGameTime(),
                    "Animation timestamp is not the monotonic world tick clock");
            })) {
            context.waitTicks(30);
            context.runOnClient(mc-> {
                mc.player.startFallFlying();
                var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
                var frame=((NativeRenderState)state).bedrockCompatFrame();
                check(frame!=null&&frame.draws().stream().anyMatch(d->d.texture().getPath().contains("elytra")),
                    "Gliding elytra lost its wings");
            });
            context.takeScreenshot("reference-player-elytra-flight");
            }
            server.runOnServer(s->s.getPlayerList().getPlayers().getFirst().stopFallFlying());
            context.runOnClient(mc->mc.player.stopFallFlying());
            server.runCommand("gamemode creative @a");
            server.runCommand("execute as @a run tp @s -2 99 4 180 0");
            server.runCommand("item replace entity @a armor.head with minecraft:netherite_helmet");
            server.runCommand("item replace entity @a armor.chest with minecraft:netherite_chestplate");
            server.runCommand("item replace entity @a armor.legs with minecraft:netherite_leggings");
            server.runCommand("item replace entity @a armor.feet with minecraft:netherite_boots");
            context.waitTicks(20);context.takeScreenshot("reference-player-netherite-front");
            server.runCommand("gamemode survival @a");
            context.waitTicks(3);
            context.runOnClient(mc->mc.setScreenAndShow(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
            context.waitTicks(10);context.takeScreenshot("reference-player-inventory");
            context.runOnClient(mc->mc.setScreenAndShow(null));
            server.runCommand("time set midnight");
            server.runCommand("gamerule minecraft:players_sleeping_percentage 101");
            server.runCommand("setblock -2 99 4 minecraft:red_bed[part=foot,facing=south]");
            server.runCommand("setblock -2 99 5 minecraft:red_bed[part=head,facing=south]");
            server.runOnServer(s->s.getPlayerList().getPlayers().getFirst().startSleeping(new net.minecraft.core.BlockPos(-2,99,5)));
            context.waitFor(mc->mc.player.isSleeping());context.waitTicks(30);
            context.runOnClient(mc-> {
                var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
                var frame=((NativeRenderState)state).bedrockCompatFrame();
                check(frame!=null&&!frame.draws().isEmpty(),"Sleeping player lost its native model");
                var vertices=frame.draws().stream().flatMap(d->d.mesh().stream()).flatMap(q->q.vertices().stream()).toList();
                double height=vertices.stream().mapToDouble(v->v.y()).max().orElseThrow()-vertices.stream().mapToDouble(v->v.y()).min().orElseThrow();
                check(height<24,"Sleeping player is still standing: model height="+height);
            });
            context.takeScreenshot("reference-player-sleeping");
            server.runOnServer(s->s.getPlayerList().getPlayers().getFirst().stopSleeping());
            context.waitFor(mc->!mc.player.isSleeping());
            server.runCommand("gamemode creative @a");
            for(String slot:List.of("head","chest","legs","feet"))
                server.runCommand("item replace entity @a armor."+slot+" with minecraft:air");
            context.runOnClient(mc->mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
            server.runCommand("execute as @a run tp @s 0 99 4 180 0");
            context.runOnClient(mc-> {
                ArmorStand stand=null;
                for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand candidate)stand=candidate;
                check(stand!=null,"Reference armor stand did not spawn");
                var state=mc.getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand,0);
                var frame=((NativeRenderState)state).bedrockCompatFrame();
                check(frame!=null&&!frame.draws().isEmpty(),"Reference model failed: "+NativeEntityResources.errors().stream().filter(e->e.startsWith("minecraft:armor_stand:")).toList());
            });
            context.takeScreenshot("reference-armor-stand");
            server.runCommand("execute as @a run tp @s 2 99 2 135 10");
            context.waitTicks(5);
            for(String tool:List.of("mace","wooden_sword","firework_rocket","spruce_sapling","red_carpet","allay_spawn_egg")) {
                server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] weapon.mainhand with minecraft:"+tool);
                context.waitFor(mc-> {
                    for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand stand)
                        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stand.getMainHandItem().getItem()).getPath().equals(tool);
                    return false;
                });
                context.waitTicks(8);context.takeScreenshot("reference-held-"+tool);
            }
            server.runCommand("execute as @a run tp @s 0 99 4 180 0");
            server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] weapon.mainhand with minecraft:diamond_sword");
            server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] armor.head with minecraft:diamond_helmet");
            server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] armor.chest with minecraft:diamond_chestplate");
            server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] armor.legs with minecraft:diamond_leggings");
            server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] armor.feet with minecraft:diamond_boots");
            server.runCommand("data merge entity @e[type=minecraft:armor_stand,limit=1] {\"bedrock_compat:pose_index\":3}");
            context.waitFor(mc-> {
                for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand stand)
                    return ((BedrockArmorStandAccess)stand).bedrockCompatState().poseIndex()==3&&!stand.getMainHandItem().isEmpty()
                        &&!stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty();
                return false;
            });
            context.runOnClient(mc-> {
                for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand stand) {
                    var state=mc.getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand,1);
                    var frame=((NativeRenderState)state).bedrockCompatFrame();
                    Set<String> swordBones=new HashSet<>();
                    var resources=NativeEntityResources.snapshot();
                    for(var attachable:resources.attachables().values())if(attachable.model().identifier().equals("minecraft:diamond_sword"))
                        for(var alias:attachable.model().aliases().values()) {
                            var geometry=resources.geometries().get(alias.toString());
                            if(geometry!=null)for(var bone:geometry.bones())if(!bone.cubes().isEmpty()||!bone.polygons().isEmpty()||!bone.textureMeshes().isEmpty())swordBones.add(bone.name());
                        }
                    check(frame!=null&&frame.draws().stream().flatMap(draw->draw.mesh().stream()).anyMatch(quad->swordBones.contains(quad.bone())),
                        "Reference sword attachable was not rendered: bones="+swordBones+" errors="+NativeEntityResources.errors());
                    check(frame.attachments().isEmpty(),"Reference sword was duplicated by its Java held model");
                }
            });
            context.takeScreenshot("reference-armor-stand-equipped");
            for(String slot:List.of("armor.head","armor.chest","armor.legs","armor.feet"))
                server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] "+slot+" with minecraft:air");
            for(String mode:List.of("§§","§§§§")) {
                server.runOnServer(s->stand(s).setCustomName(net.minecraft.network.chat.Component.literal(mode)));
                context.waitFor(mc-> {
                    for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand stand)return stand.getName().getString().equals(mode);
                    return false;
                });
                for(String item:List.of("stone","diamond_sword","glow_item_frame","diamond_helmet","wheat","cobweb","copper_lantern","diamond")) {
                    server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] weapon.mainhand with minecraft:"+item);
                    context.waitFor(mc-> {
                        for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand stand)
                            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stand.getMainHandItem().getItem()).getPath().equals(item)
                                &&stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty();
                        return false;
                    });
                    context.waitTicks(3);
                    context.runOnClient(mc-> {
                        for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand stand) {
                            var rendered=mc.getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand,1);
                            var frame=((NativeRenderState)rendered).bedrockCompatFrame();
                            check(rendered.nameTag==null,"Control-only armor stand nameplate was not suppressed");
                            check(frame!=null&&frame.originY()==0,"Control-name display introduced a world-space height offset");
                            check(frame!=null&&!frame.draws().isEmpty(),"Reference "+mode+" / "+item+" did not render: "+NativeEntityResources.errors());
                            check(frame.attachments().isEmpty(),"Reference "+mode+" / "+item+" fell back to Java item: "+NativeEntityResources.errors());
                            if(mode.equals("§§")&&item.equals("glow_item_frame")) {
                                check(frame.draws().stream().flatMap(d->d.mesh().stream()).noneMatch(q->q.bone().equals("head4m36")||q.bone().equals("waist")),
                                    "Prop holder reappeared after attachable parent inputs went stale");
                                check(frame.draws().stream().allMatch(d->d.material().colorWrite()),"Portal holdout geometry was rendered as color");
                                for(var draw:frame.draws())if(draw.material().stencil()!=null&&draw.material().stencil().function().equals("Equal")) {
                                    var portal=dev.matanyah.bedrockcompat.client.entity.NativePortalRenderTypes.overlay(
                                        net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(draw.texture()),draw.texture());
                                    check(portal.pipeline().getDepthStencilState().depthTest()==com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS,
                                        "Portal interior still depth-tests against terrain behind the aperture");
                                    check(!portal.pipeline().getDepthStencilState().writeDepth(),"Portal interior leaked virtual-room depth into the world");
                                }
                            }
                            if(item.equals("diamond_sword")||item.equals("stone")) {
                                Set<String> itemBones=new HashSet<>();
                                var resources=NativeEntityResources.snapshot();
                                for(var attachable:resources.attachables().values())if(attachable.items().containsKey("minecraft:"+item))
                                    for(var alias:attachable.model().aliases().values()) {
                                        var geometry=resources.geometries().get(alias.toString());
                                        if(geometry!=null)for(var bone:geometry.bones())
                                            if(!bone.cubes().isEmpty()||!bone.polygons().isEmpty()||!bone.textureMeshes().isEmpty())itemBones.add(bone.name());
                                    }
                                check(frame.draws().stream().flatMap(d->d.mesh().stream()).anyMatch(q->itemBones.contains(q.bone())),
                                    "Reference "+mode+" / "+item+" drew only the holder, not its item geometry");
                            }
                        }
                    });
                    context.takeScreenshot("reference-"+(mode.length()==2?"prop":"item")+"-"+item);
                for(int index=0;index<BedrockArmorStandState.POSE_COUNT;index++) {
                    final int poseIndex=index;
                    server.runCommand("data merge entity @e[type=minecraft:armor_stand,limit=1] {\"bedrock_compat:pose_index\":"+index+"}");
                    context.waitFor(mc-> {
                        for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand stand)
                            return ((BedrockArmorStandAccess)stand).bedrockCompatState().poseIndex()==poseIndex;
                        return false;
                    });
                    context.waitTicks(2);
                    context.runOnClient(mc-> {
                        for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand stand) {
                            var frame=((NativeRenderState)mc.getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand,1)).bedrockCompatFrame();
                            check(frame!=null&&!frame.draws().isEmpty(),"Reference "+mode+" pose "+poseIndex+" failed: "+NativeEntityResources.errors());
                        }
                    });
                }
                }
            }
            String audit=System.getenv("BEDROCK_REFERENCE_ENTITIES");
            if(audit!=null&&!audit.isBlank()) {
                List<String> missing=new ArrayList<>();
                for(String type:audit.split(",")) {
                    if(type.equals("allay")||type.equals("warden"))server.runCommand("time set midnight");
                    server.runCommand("summon minecraft:"+type+" 2 99 0 {NoAI:1b,Silent:1b,Invulnerable:1b,NoGravity:1b}");
                    context.waitFor(mc-> {
                        for(var e:mc.level.entitiesForRendering())
                            if(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals(type))return true;
                        return false;
                    });
                    context.waitTicks(3);
                    context.runOnClient(mc-> {
                        for(var e:mc.level.entitiesForRendering())
                            if(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals(type)) {
                                var state=mc.getEntityRenderDispatcher().getRenderer(e).createRenderState(e,1);
                                var frame=((NativeRenderState)state).bedrockCompatFrame();
                                if(frame==null||frame.draws().isEmpty())missing.add(type);
                            }
                    });
                    context.waitTicks(30);
                    context.takeScreenshot("reference-"+type);
                    if(type.equals("sulfur_cube"))SulfurCubeChecks.verify(context,server::runCommand);
                    if(type.equals("villager")) {
                        server.runCommand("data merge entity @e[type=minecraft:villager,limit=1] {VillagerData:{profession:\"minecraft:fisherman\",level:1,type:\"minecraft:plains\"}}");
                        context.waitTicks(10);context.takeScreenshot("reference-villager-hat");
                        server.runCommand("setblock 2 99 -1 minecraft:red_bed[part=foot,facing=south]");
                        server.runCommand("setblock 2 99 0 minecraft:red_bed[part=head,facing=south]");
                        server.runOnServer(s->{
                            for(var level:s.getAllLevels())for(var entity:level.getAllEntities())
                                if(entity instanceof net.minecraft.world.entity.npc.villager.Villager villager)
                                    villager.startSleeping(new net.minecraft.core.BlockPos(2,99,0));
                        });
                        context.waitTicks(40);context.takeScreenshot("reference-villager-sleeping");
                    }
                    if(type.equals("villager")||type.equals("zombie")) {
                        server.runCommand("data merge entity @e[type=minecraft:"+type+",limit=1] "+
                            (type.equals("villager")?"{Age:-24000}":"{IsBaby:1b}"));
                        context.waitTicks(5);
                        context.runOnClient(mc-> {
                            for(var e:mc.level.entitiesForRendering())
                                if(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals(type)) {
                                    var state=(net.minecraft.client.renderer.entity.state.LivingEntityRenderState)
                                        mc.getEntityRenderDispatcher().getRenderer(e).createRenderState(e,1);
                                    check(state.ageScale<1,"Baby did not expose engine age scaling: "+type);
                                    var frame=((NativeRenderState)state).bedrockCompatFrame();
                                    check(frame!=null&&!frame.draws().isEmpty(),"Baby native rendering failed: "+type);
                                }
                        });
                        context.takeScreenshot("reference-baby-"+type);
                    }
                    server.runCommand("kill @e[type=minecraft:"+type+"]");
                }
                check(missing.isEmpty(),"Reference entities failed: "+missing+"; runtime errors: "+NativeEntityResources.errors().stream()
                    .filter(e->e.startsWith("minecraft:")).toList());
            }
            server.runCommand("time set noon");
            server.runCommand("fill -2 99 0 0 100 0 minecraft:grass_block");
            server.runCommand("execute as @a run tp @s -1 99 4 facing -1 100 0");
            server.runCommand("item replace entity @a weapon.mainhand with minecraft:grass_block");
            context.waitTicks(20);world.getConnection().waitForChunksRender();
            context.runOnClient(mc->{
                try(var stream=mc.getResourceManager().open(net.minecraft.resources.Identifier.withDefaultNamespace("models/block/grass_block.json"))) {
                    var model=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject();
                    // Validate the generated asset with Java's loader, not just Gson acceptance.
                    net.minecraft.client.resources.model.cuboid.CuboidModel.fromStream(new java.io.StringReader(model.toString()));
                    int clipped=0;
                    for(var element:model.getAsJsonArray("elements"))for(var entry:element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                        var face=entry.getValue().getAsJsonObject();
                        if(face.has("tintindex")&&!entry.getKey().equals("up")) {
                            var uv=face.getAsJsonArray("uv");
                            check(uv!=null,"Grass tint still covers the full dirt face");clipped++;
                        }
                    }
                    check(clipped>0,"Reference grass mask geometry was not generated");
                    for(String direction:List.of("north","south","east","west")) {
                        var regions=new ArrayList<double[]>();double area=0;
                        for(var element:model.getAsJsonArray("elements")) {
                            var face=element.getAsJsonObject().getAsJsonObject("faces").getAsJsonObject(direction);if(face==null)continue;
                            var uv=face.getAsJsonArray("uv");check(uv!=null,"Full coplanar grass base survived");
                            double[] rectangle={uv.get(0).getAsDouble(),uv.get(1).getAsDouble(),uv.get(2).getAsDouble(),uv.get(3).getAsDouble()};
                            for(var previous:regions)check(Math.min(previous[2],rectangle[2])<=Math.max(previous[0],rectangle[0])||Math.min(previous[3],rectangle[3])<=Math.max(previous[1],rectangle[1]),"Reference grass sides overlap: "+direction);
                            regions.add(rectangle);area+=(rectangle[2]-rectangle[0])*(rectangle[3]-rectangle[1]);
                        }
                        check(Math.abs(area-256)<1e-8,"Reference grass side has missing coverage: "+direction);
                    }
                }catch(java.io.IOException error){throw new AssertionError(error);}
            });
            context.takeScreenshot("reference-grass-untinted-dirt");
            server.runCommand("execute as @a run tp @s 3 99 4 facing -1 100 0");context.waitTicks(10);
            context.takeScreenshot("reference-grass-side-oblique");
        }
        context.runOnClient(mc->BedrockOptionsState.setSelectedIds(List.of()));
        reload(context,false);
    }
    private static ArmorStand stand(net.minecraft.server.MinecraftServer server) {
        for(var level:server.getAllLevels())for(var entity:level.getAllEntities())if(entity instanceof ArmorStand stand)return stand;
        return null;
    }
    private static void priority(ClientGameTestContext context,UUID standId,Path bedrockPack) throws Exception {
        var retiredImage=context.computeOnClient(mc-> {
            for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.projectile.arrow.Arrow) {
                var state=mc.getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity,1);
                var frame=((NativeRenderState)state).bedrockCompatFrame();
                return ((net.minecraft.client.renderer.texture.DynamicTexture)mc.getTextureManager().getTexture(frame.draws().getFirst().texture())).getPixels();
            }
            throw new AssertionError("No masked texture available to test reload disposal");
        });
        Path javaPack=context.computeOnClient(mc->mc.gameDirectory.toPath().resolve("resourcepacks/native-priority-test"));
        var texture=javaPack.resolve("assets/minecraft/textures/entity/armorstand/armorstand.png");
        Files.createDirectories(texture.getParent());
        Files.copy(bedrockPack.resolve("textures/entity/armor_stand.png"),texture,StandardCopyOption.REPLACE_EXISTING);
        Path boatTexture=javaPack.resolve("assets/minecraft/textures/entity/boat/oak.png");
        Files.createDirectories(boatTexture.getParent());
        Files.copy(bedrockPack.resolve("textures/entity/armor_stand.png"),boatTexture,StandardCopyOption.REPLACE_EXISTING);
        Path arrowTexture=javaPack.resolve("assets/minecraft/textures/entity/projectiles/arrow.png");
        Files.createDirectories(arrowTexture.getParent());
        Files.copy(bedrockPack.resolve("textures/entity/armor_stand.png"),arrowTexture,StandardCopyOption.REPLACE_EXISTING);
        var metadata=new net.minecraft.server.packs.metadata.pack.PackMetadataSection(net.minecraft.network.chat.Component.literal("Priority test"),
            net.minecraft.SharedConstants.getCurrentVersion().packVersion(net.minecraft.server.packs.PackType.CLIENT_RESOURCES).minorRange());
        var json=new com.google.gson.JsonObject();
        json.add("pack",net.minecraft.server.packs.metadata.pack.PackMetadataSection.CLIENT_TYPE.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE,metadata).getOrThrow());
        Files.writeString(javaPack.resolve("pack.mcmeta"),json.toString());
        for(boolean nativeAbove:List.of(false,true)) {
            var reload=context.computeOnClient(mc-> {
                var repository=mc.getResourcePackRepository();repository.reload();
                var ids=new ArrayList<>(repository.getSelectedIds());
                ids.remove(VirtualCompatibilityPackProvider.PACK_ID);ids.remove("file/native-priority-test");
                ids.add(nativeAbove?"file/native-priority-test":VirtualCompatibilityPackProvider.PACK_ID);
                ids.add(nativeAbove?VirtualCompatibilityPackProvider.PACK_ID:"file/native-priority-test");
                repository.setSelected(ids);
                return mc.reloadResourcePacks();
            });
            context.waitFor(mc->reload.isDone(),1200);reload.join();
            context.runOnClient(mc-> {
                check(retiredImage.isClosed(),"Resource reload retained the old masked texture image");
                var stand=mc.level.getEntity(standId);
                var state=mc.getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand,1);
                check((((NativeRenderState)state).bedrockCompatFrame()!=null)==nativeAbove,
                    "Pack-priority mismatch; nativeAbove="+nativeAbove);
                for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat) {
                    var boatState=mc.getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity,1);
                    String type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
                    check((((NativeRenderState)boatState).bedrockCompatFrame()!=null)==(!type.equals("oak_boat")||nativeAbove),
                        "Boat-specific texture priority mismatch: "+type+"; nativeAbove="+nativeAbove);
                }
                for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.projectile.arrow.Arrow) {
                    var arrowState=mc.getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity,1);
                    check((((NativeRenderState)arrowState).bedrockCompatFrame()!=null)==nativeAbove,
                        "Projectile texture priority mismatch; nativeAbove="+nativeAbove);
                    if(nativeAbove)checkMaskedTexture(mc,((NativeRenderState)arrowState).bedrockCompatFrame());
                }
            });
        }
        // A small-only CEM must not suppress native rendering on an adult stand.
        Files.delete(texture);
        Files.delete(boatTexture);
        Files.delete(arrowTexture);
        Path model=javaPack.resolve("assets/minecraft/optifine/cem/armor_stand_small.jem");
        Files.createDirectories(model.getParent());
        Files.writeString(model,"""
            {"textureSize":[64,64],"models":[{"part":"head","id":"priority_test_head","invertAxis":"xy",
             "boxes":[{"coordinates":[-4,-8,-4,8,8,8],"textureOffset":[0,0]}]}]}
            """);
        var summon=context.computeOnClient(mc-> {
            var server=mc.getSingleplayerServer();
            return server.submit(()->server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "summon minecraft:armor_stand -2 99 0 {Small:1b,NoGravity:1b}"));
        });
        context.waitFor(mc->summon.isDone());summon.join();
        context.waitFor(mc-> {
            for(var entity:mc.level.entitiesForRendering())if(entity instanceof ArmorStand small&&small.isSmall())return true;
            return false;
        });
        boolean cemAvailable=net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("entity_model_features")
            ||net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("optifine")
            ||net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("optifabric");
        for(boolean nativeAbove:List.of(false,true)) {
            var future=context.computeOnClient(mc-> {
                var repository=mc.getResourcePackRepository();repository.reload();
                var ids=new ArrayList<>(repository.getSelectedIds());
                ids.remove(VirtualCompatibilityPackProvider.PACK_ID);ids.remove("file/native-priority-test");
                ids.add(nativeAbove?"file/native-priority-test":VirtualCompatibilityPackProvider.PACK_ID);
                ids.add(nativeAbove?VirtualCompatibilityPackProvider.PACK_ID:"file/native-priority-test");
                repository.setSelected(ids);return mc.reloadResourcePacks();
            });
            context.waitFor(mc->future.isDone(),1200);future.join();
            context.runOnClient(mc-> {
                var adult=mc.level.getEntity(standId);
                var adultState=mc.getEntityRenderDispatcher().getRenderer(adult).createRenderState(adult,1);
                check(((NativeRenderState)adultState).bedrockCompatFrame()!=null,"Small-only Java model suppressed adult native stand");
                for(var entity:mc.level.entitiesForRendering())if(entity instanceof ArmorStand small&&small.isSmall()) {
                    var state=mc.getEntityRenderDispatcher().getRenderer(small).createRenderState(small,1);
                    check((((NativeRenderState)state).bedrockCompatFrame()!=null)==(nativeAbove||!cemAvailable),
                        "Small stand CEM priority mismatch; nativeAbove="+nativeAbove+", CEM="+cemAvailable);
                }
            });
        }
        context.runOnClient(mc->mc.getResourcePackRepository().removePack("file/native-priority-test"));
    }
    private static void checkCats(ClientGameTestContext context,boolean posed) {
        context.runOnClient(mc-> {
            Set<String> seen=new HashSet<>();
            for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.animal.feline.Cat cat) {
                String coat=cat.getVariant().unwrapKey().orElseThrow().identifier().getPath();seen.add(coat);
                var state=mc.getEntityRenderDispatcher().getRenderer(cat).createRenderState(cat,1);
                var frame=((NativeRenderState)state).bedrockCompatFrame();
                check(frame!=null&&!frame.draws().isEmpty(),"Cat did not produce native geometry: "+coat+"; "+NativeEntityResources.errors());
                check(frame.draws().getFirst().texture().getPath().equals("textures/entity/cat/"+coat+".png"),"Cat selected wrong coat texture: "+coat);
                var vertices=frame.draws().stream().flatMap(d->d.mesh().stream()).filter(q->q.bone().equals("cat_state"))
                    .flatMap(q->q.vertices().stream()).toList();
                check(Math.abs(vertices.stream().mapToDouble(v->v.x()).min().orElseThrow()-CAT_COATS.indexOf(coat))<1e-4,"Cat variant animation used Java ordering: "+coat);
                check(Math.abs(vertices.stream().mapToDouble(v->v.y()).min().orElseThrow()-(posed?1:0))<1e-4,"Cat sitting query did not follow synchronized pose");
                check(Math.abs(vertices.stream().mapToDouble(v->v.z()).min().orElseThrow()-(posed?1:0))<1e-4,"Cat lying query did not follow synchronized pose");
                // Java AI owns cat crouching and can reset a manually assigned server
                // pose. Check the read-only input against a fresh isolated view instead.
                var oldPose=cat.getPose();NativeEntityRenderer.Frame crouching;
                try {
                    cat.setPose(posed?net.minecraft.world.entity.Pose.CROUCHING:net.minecraft.world.entity.Pose.STANDING);
                    crouching=NativeEntityRenderer.extract(cat,state,1,frame.draws().getFirst().texture(),NativeEntityRenderer.View.UI);
                }finally{cat.setPose(oldPose);}
                double crouch=crouching.draws().stream().flatMap(d->d.mesh().stream()).filter(q->q.bone().equals("cat_crouch"))
                    .flatMap(q->q.vertices().stream()).mapToDouble(v->v.y()).min().orElseThrow();
                check(Math.abs(crouch-(posed?1:0))<1e-4,"Cat crouching pose was mistaken for player shift input");
            }
            check(seen.equals(Set.copyOf(CAT_COATS)),"Cat regression did not exercise every vanilla coat: "+seen);
        });
    }
    private static void checkMaskedTexture(net.minecraft.client.Minecraft minecraft,NativeEntityRenderer.Frame frame) {
        check(frame.draws().size()==1&&frame.draws().getFirst().material().masked(),"Masked material was emitted as separate overlapping layers");
        var texture=minecraft.getTextureManager().getTexture(frame.draws().getFirst().texture());
        check(texture instanceof net.minecraft.client.renderer.texture.DynamicTexture,"Masked texture was not registered");
        var image=((net.minecraft.client.renderer.texture.DynamicTexture)texture).getPixels();
        check(image.getWidth()==32&&image.getHeight()==16,"Masked texture resolution was lost");
        check(image.getPixel(0,0)==0xff00ff80,"Transparent colored mask did not preserve base");
        check(image.getPixel(8,0)==0,"Transparent black mask failed to cut out base");
        check(image.getPixel(16,0)==0xffff0000,"Opaque mask did not replace base");
        check(image.getPixel(24,0)==0xff00ff80,"Fractional colored mask was alpha-blended instead of masked");
    }
    private static void reload(ClientGameTestContext context,boolean enable) {
        long generation=dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackRuntime.reloadGeneration();
        dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackRuntime.selectionChanged();
        check(dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackRuntime.reloadGeneration()==generation,
            "Selection changes must defer generation until reload");
        var future=context.computeOnClient(mc-> {
            mc.getResourcePackRepository().reload();
            if(enable)mc.getResourcePackRepository().addPack(VirtualCompatibilityPackProvider.PACK_ID);
            return mc.reloadResourcePacks();
        });
        context.waitFor(mc->future.isDone(),1200);future.join();
        if(enable) {
            check(dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackRuntime.reloadGeneration()==generation+1,
                "A resource reload should generate once, not again for metadata");
            AddonMenuReloadChecks.run(context);
        }
    }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    private static void fixture(Path pack) throws Exception {
        Files.createDirectories(pack.resolve("entity"));Files.createDirectories(pack.resolve("models/entity"));Files.createDirectories(pack.resolve("render_controllers"));Files.createDirectories(pack.resolve("textures/entity"));
        Files.writeString(pack.resolve("manifest.json"),"""
            {"format_version":2,"header":{"name":"Native entity runtime fixture","description":"Test fixture","uuid":"c11daf7e-aef0-4ed9-bc85-131df99f3c73","version":[1,0,0],"min_engine_version":[1,20,0]},
             "modules":[{"type":"resources","uuid":"83467cc6-7d4e-4f43-9847-b90d9b1cacd9","version":[1,0,0]}]}
            """);
        Files.writeString(pack.resolve("entity/stand.json"),"""
            {"format_version":"1.10.0","minecraft:client_entity":{"description":{"identifier":"minecraft:armor_stand",
             "materials":{"default":"armor_stand","glow":"entity_emissive_alpha"},"textures":{"default":"textures/entity/armor_stand","glow":"textures/entity/emissive_fixture"},
             "enable_attachables":true,"geometry":{"default":"geometry.test_stand"},"render_controllers":["controller.render.test_stand"]}}}
            """);
        Files.writeString(pack.resolve("entity/arrow.json"),Files.readString(pack.resolve("entity/stand.json"))
            .replace("minecraft:armor_stand","minecraft:arrow").replace("geometry.test_stand","geometry.test_polygon")
            .replace("\"default\":\"armor_stand\"","\"default\":\"fixture_masked\"")
            .replace("\"default\":\"textures/entity/armor_stand\"","\"default\":\"textures/entity/armor_stand\",\"mask\":\"textures/entity/test_mask\"")
            .replace("controller.render.test_stand","controller.render.test_masked"));
        Files.createDirectories(pack.resolve("materials"));
        Files.writeString(pack.resolve("materials/fixture.material"),"""
            {"materials":{"version":"1.0.0","fixture_masked:entity_alphatest":{"+defines":["MASKED_MULTITEXTURE"]}}}
            """);
        Files.writeString(pack.resolve("render_controllers/masked.json"),"""
            {"render_controllers":{"controller.render.test_masked":{"geometry":"Geometry.default",
             "materials":[{"*":"Material.default"}],"textures":["Texture.default","Texture.mask"]}}}
            """);
        Files.writeString(pack.resolve("entity/boat.json"),"""
            {"format_version":"1.10.0","minecraft:client_entity":{"description":{"identifier":"minecraft:boat",
             "materials":{"default":"entity_alphatest"},"textures":{"default":"textures/entity/armor_stand"},
             "geometry":{"default":"geometry.test_boat"},"animations":{"variant":"animation.test.boat",
              "lerp":"controller.animation.test.keyframe","lerp_clip":"animation.test.keyframe","lerp_follow":"animation.test.follow"},
             "scripts":{"animate":["variant","lerp"]},"render_controllers":["controller.render.test_boat"]}}}
            """);
        Files.writeString(pack.resolve("entity/chest_boat.json"),Files.readString(pack.resolve("entity/boat.json"))
            .replace("minecraft:boat","minecraft:chest_boat"));
        Files.writeString(pack.resolve("entity/minecart.json"),Files.readString(pack.resolve("entity/boat.json"))
            .replace("minecraft:boat","minecraft:minecart").replace("animation.test.boat","animation.test.cart")
            .replace("\"scripts\":{\"animate\":[\"variant\",\"lerp\"]}",
                "\"scripts\":{\"pre_animation\":[\"v.hurt=q.hurt_time-q.frame_alpha;\"],\"animate\":[\"variant\",\"lerp\"]}"));
        Files.writeString(pack.resolve("models/entity/boat.json"),"""
            {"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.test_boat","texture_width":16,"texture_height":16},
             "bones":[{"name":"hull","cubes":[{"origin":[-10,0,-6],"size":[20,4,12]}]},
             {"name":"variant_marker","cubes":[{"origin":[0,4,0],"size":[1,2,1]}]},
             {"name":"alpha_marker","cubes":[{"origin":[-4,0,0],"size":[1,1,1]}]},
             {"name":"lerp_marker","cubes":[{"origin":[0,0,0],"size":[1,1,1]}]},
             {"name":"damage_marker","cubes":[{"origin":[0,0,0],"size":[1,1,1]}]},
             {"name":"rider_marker","cubes":[{"origin":[-2,4,0],"size":[1,2,1]}]}]}]}
            """);
        Files.writeString(pack.resolve("render_controllers/boat.json"),"""
            {"render_controllers":{"controller.render.test_boat":{"geometry":"Geometry.default",
             "textures":["Texture.default"],"materials":[{"*":"Material.default"}],
             "part_visibility":[{"rider_marker":"q.has_rider"}]}}}
            """);
        Files.writeString(pack.resolve("models/entity/polygon.json"),"""
            {"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.test_polygon_base","texture_width":16,"texture_height":16},
             "bones":[{"name":"polygon","poly_mesh":{"positions":[[-8,0,0],[8,0,0],[0,24,0]],
             "normals":[[0,0,1],[0,0,-1]],"uvs":[[0,16],[16,16],[8,0]],
             "polys":[[[0,0,0],[1,0,1],[2,0,2]],[[2,1,2],[1,1,1],[0,1,0]]]}}]}]}
            """);
        Files.writeString(pack.resolve("models/entity/polygon_child.json"),"""
            {"format_version":"1.8.0","geometry.test_polygon:geometry.test_polygon_base":{"bones":[
             {"name":"inheritance_marker","parent":"polygon","cubes":[{"origin":[-1,2,0],"size":[2,2,2]}]},
             {"name":"hidden_marker","neverRender":true,"cubes":[{"origin":[-20,0,-20],"size":[40,40,40]}]}]}}
            """);
        Files.writeString(pack.resolve("entity/player.json"),"""
            {"format_version":"1.10.0","minecraft:client_entity":{"description":{"identifier":"minecraft:player",
             "geometry":{"default":"geometry.humanoid.custom","other":"geometry.test_stand"},"textures":{"default":"textures/entity/steve"},
             "materials":{"default":"entity_alphatest"},"animations":{"arm":"animation.test.player"},
             "scripts":{"animate":["arm"],"scale":"q.relative_block_has_any_tag(0,-2,0,'minecraft:base_stone_overworld')?1:2"},
             "render_controllers":[{"controller.render.test.player":"!v.is_first_person"},{"controller.render.player.first_person":"v.is_first_person"}]}}}
            """);
        Files.writeString(pack.resolve("render_controllers/player.json"),"""
            {"render_controllers":{"controller.render.test.player":{"geometry":"v.fixture_shape?Geometry.other:Geometry.default",
             "materials":[{"*":"Material.default"}],"textures":["Texture.default"]}}}
            """);
        Files.createDirectories(pack.resolve("animations"));
        var catTextures=new com.google.gson.JsonObject();var catArray=new com.google.gson.JsonArray();
        for(String coat:CAT_COATS){catTextures.addProperty(coat,"textures/entity/cat/"+coat);catArray.add("Texture."+coat);}
        var catDocument=com.google.gson.JsonParser.parseString("""
            {"format_version":"1.10.0","minecraft:client_entity":{"description":{"identifier":"minecraft:cat",
             "materials":{"default":"entity_alphatest"},"geometry":{"default":"geometry.fixture_cat"},
             "animations":{"state":"animation.fixture_cat"},"scripts":{"animate":["state"]},
             "render_controllers":["controller.render.fixture_cat"]}}}
            """).getAsJsonObject();
        catDocument.getAsJsonObject("minecraft:client_entity").getAsJsonObject("description").add("textures",catTextures);
        Files.writeString(pack.resolve("entity/cat.json"),catDocument.toString());
        Files.writeString(pack.resolve("models/entity/cat.json"),"""
            {"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.fixture_cat","texture_width":16,"texture_height":16},
             "bones":[{"name":"cat_state","cubes":[{"origin":[0,0,0],"size":[1,1,1]}]},
                      {"name":"cat_crouch","cubes":[{"origin":[0,0,0],"size":[1,1,1]}]}]}]}
            """);
        Files.writeString(pack.resolve("animations/cat.json"),"""
            {"format_version":"1.8.0","animations":{"animation.fixture_cat":{"loop":true,"bones":{
             "cat_state":{"position":["q.variant","q.is_sitting","q.is_laying_down"]},
             "cat_crouch":{"position":[0,"q.is_sneaking",0]}}}}}
            """);
        Files.writeString(pack.resolve("render_controllers/cat.json"),"""
            {"render_controllers":{"controller.render.fixture_cat":{"geometry":"Geometry.default",
             "materials":[{"*":"Material.default"}],"textures":["Array.coats[q.variant]"],"arrays":{"textures":{"Array.coats":
            """+catArray+"}}}}}");
        Files.writeString(pack.resolve("animations/boat.json"),"""
            {"format_version":"1.8.0","animations":{"animation.test.boat":{"loop":true,
             "bones":{"variant_marker":{"position":["q.variant",0,0]},
              "lerp_marker":{"position":[3,0,0]},
              "alpha_marker":{"position":[0,"q.frame_alpha",0]},
              "damage_marker":{"position":["q.hurt_time-q.frame_alpha","q.structural_integrity","q.hurt_direction"]}}}}}
            """);
        Files.writeString(pack.resolve("animations/keyframe.json"),"""
            {"format_version":"1.8.0","animations":{"animation.test.keyframe":{
             "loop":true,"animation_length":1,"anim_time_update":"0.25","override_previous_animation":true,
             "bones":{"lerp_marker":{"position":{"0":["q.key_frame_lerp_time",0,0],"1":["q.key_frame_lerp_time",0,0]},
              "rotation":[0],"scale":[1]}}}}}
            """);
        Files.writeString(pack.resolve("animations/follow.json"),"""
            {"format_version":"1.8.0","animations":{"animation.test.follow":{
             "bones":{"lerp_marker":{"position":["this",0,0]}}}}}
            """);
        Files.createDirectories(pack.resolve("animation_controllers"));
        Files.writeString(pack.resolve("animation_controllers/keyframe.json"),"""
            {"format_version":"1.10.0","animation_controllers":{"controller.animation.test.keyframe":{
             "states":{"default":{"animations":["lerp_clip","lerp_follow"]}}}}}
            """);
        Files.writeString(pack.resolve("animations/minecart.json"),Files.readString(pack.resolve("animations/boat.json"))
            .replace("animation.test.boat","animation.test.cart").replace("q.variant","0")
            .replace("q.hurt_time-q.frame_alpha","v.hurt"));
        Files.writeString(pack.resolve("animations/player.json"),"""
            {"format_version":"1.8.0","animations":{"animation.test.player":{"loop":true,"animation_length":2,
             "bones":{"rightarm":{"rotation":["v.is_first_person?65:math.sin(q.anim_time*180)*20",0,0]},
                      "leftarm":{"rotation":["v.is_first_person?65:-math.sin(q.anim_time*180)*20",0,0]}}}}}
            """);
        Files.writeString(pack.resolve("models/entity/stand.json"),"""
            {"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.test_stand","texture_width":16,"texture_height":16},
             "bones":[{"name":"custom_root","cubes":[{"origin":[-4,12,-2],"size":[8,12,4],"uv":[0,0]}]},
              {"name":"head","parent":"custom_root","pivot":[0,24,0],"cubes":[{"origin":[-4,24,-4],"size":[8,8,8]}]},
              {"name":"body","parent":"custom_root","pivot":[0,24,0]},
              {"name":"rightArm","parent":"custom_root","pivot":[-5,22,0]},
              {"name":"rightItem","parent":"rightArm","pivot":[-5,12,0],"neverRender":true},
              {"name":"left_arm","parent":"custom_root","pivot":[5,22,0]},
              {"name":"rightLeg","parent":"custom_root","pivot":[-2,12,0]},
              {"name":"left_leg","parent":"custom_root","pivot":[2,12,0]}]}]}
            """);
        Files.writeString(pack.resolve("render_controllers/stand.json"),"""
            {"render_controllers":{"controller.render.test_stand":{
             "arrays":{"geometries":{"Array.shared":["Array.leaf"],"Array.leaf":["Geometry.default"]},
                       "materials":{"Array.shared":["Array.leaf"],"Array.leaf":["Material.default"]},
                       "textures":{"Array.shared":["Array.leaf"],"Array.leaf":["Texture.default"]}},
             "geometry":"Array.shared[0]","materials":[{"*":"q.is_name_any('emissive_fixture')?Material.glow:Array.shared[0]"}],
             "textures":["q.is_name_any('emissive_fixture')?Texture.glow:Array.shared[0]"],"part_visibility":[{"*":"!q.is_name_any('hidden')"}]}}}
            """);
        Files.createDirectories(pack.resolve("attachables"));
        Files.writeString(pack.resolve("attachables/prop.json"),"""
            {"minecraft:attachable":{"description":{"identifier":"test:held_prop",
             "item":{"minecraft:iron_sword":"q.is_owner_identifier_any('minecraft:armor_stand')"},
             "geometry":{"default":"geometry.fixture_prop"},"textures":{"default":"textures/entity/fixture_prop"},
             "materials":{"default":"entity_alphatest"},"animations":{"move":"animation.fixture_prop"},
             "scripts":{"parent_setup":"v.fixture_equipment_ready = 1;",
              "pre_animation":["v.fixture_owner_ready = c.owning_entity->v.fixture_equipment_ready;"],
              "animate":["move"]},"render_controllers":["controller.render.fixture_prop"]}}}
            """);
        Files.writeString(pack.resolve("models/entity/prop.json"),"""
            {"minecraft:geometry":[{"description":{"identifier":"geometry.fixture_prop","texture_width":16,"texture_height":16},
             "bones":[{"name":"scaffold","pivot":[0,0,0]},
             {"name":"fixture_prop","parent":"scaffold","pivot":[0,20,0],"binding":"q.item_slot_to_bone_name(c.item_slot)",
             "cubes":[{"origin":[-1,20,-1],"size":[2,12,2],"uv":[0,0]}]}]}]}
            """);
        Files.writeString(pack.resolve("animations/prop.json"),"""
            {"animations":{"animation.fixture_prop":{"loop":true,"animation_length":2,
             "bones":{"fixture_prop":{"rotation":[0,"math.sin(q.anim_time*180)*30",0]}}}}}
            """);
        Files.writeString(pack.resolve("render_controllers/prop.json"),"""
            {"render_controllers":{"controller.render.fixture_prop":{"geometry":"Geometry.default",
             "materials":[{"*":"Material.default"}],"textures":["Texture.default"],
             "part_visibility":[{"*":"v.fixture_owner_ready == 1 && !q.is_name_any('hidden')"}]}}}
            """);
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,0xff00ff80);
        ImageIO.write(image,"PNG",pack.resolve("textures/entity/armor_stand.png").toFile());
        BufferedImage glowImage=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        glowImage.setRGB(0,0,0x01ffffff);
        ImageIO.write(glowImage,"PNG",pack.resolve("textures/entity/emissive_fixture.png").toFile());
        BufferedImage propImage=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)propImage.setRGB(x,y,0xffff00ff);
        ImageIO.write(propImage,"PNG",pack.resolve("textures/entity/fixture_prop.png").toFile());
        Files.createDirectories(pack.resolve("textures/entity/cat"));
        for(String coat:CAT_COATS)Files.copy(pack.resolve("textures/entity/armor_stand.png"),pack.resolve("textures/entity/cat/"+coat+".png"),StandardCopyOption.REPLACE_EXISTING);
        BufferedImage mask=new BufferedImage(32,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<32;x++)mask.setRGB(x,y,x<8?0x00ffffff:x<16?0:x<24?0xffff0000:0x80aabbcc);
        ImageIO.write(mask,"PNG",pack.resolve("textures/entity/test_mask.png").toFile());
    }
}
