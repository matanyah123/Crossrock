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
            PackConfigScreenChecks.run(context);
            Path pack=context.computeOnClient(mc->mc.gameDirectory.toPath().resolve("addons/native-entity-gametest"));
            fixture(pack);
            context.runOnClient(mc->BedrockOptionsState.setSelectedIds(List.of(PACK)));
            reload(context,true);
            check(NativeEntityResources.affectsArmorStand(),"Pack did not activate armor-stand compatibility");
            try(var world=context.worldBuilder().create()) {
                var server=world.getServer();
                server.runCommand("gamemode creative @a");
                server.runCommand("fill -5 98 -5 5 98 5 minecraft:stone");
                server.runCommand("execute as @a run tp @s 0 99 4 180 0");
                server.runCommand("summon minecraft:armor_stand 0 99 0 {NoGravity:1b}");
                server.waitFor(s->stand(s)!=null&&((BedrockArmorStandAccess)stand(s)).bedrockCompatState().active());
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
                });
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("native-armor-stand");
                server.runOnServer(s->stand(s).setCustomName(net.minecraft.network.chat.Component.literal("hidden")));
                context.waitFor(mc->mc.level.getEntity(id).getName().getString().equals("hidden"));
                context.waitTicks(2);
                context.runOnClient(mc-> {
                    var stand=mc.level.getEntity(id);
                    var state=mc.getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand,1);
                    var hidden=((NativeRenderState)state).bedrockCompatFrame();
                    check(hidden!=null&&hidden.draws().isEmpty(),"Hidden native bones fell back to the vanilla model");
                });
                server.runOnServer(s->stand(s).setCustomName(null));
                context.waitFor(mc->!mc.level.getEntity(id).hasCustomName());
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
                        check(frame!=null&&frame.draws().stream().flatMap(d->d.mesh().stream()).allMatch(q->q.bone().equals("custom_root")),"Provider-driven pack morph did not select alternate geometry");
                    });
                }finally{registration.close();}
                context.waitTicks(2);
                context.runOnClient(mc-> {
                    var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
                    var frame=((NativeRenderState)state).bedrockCompatFrame();
                    check(frame!=null&&frame.draws().stream().flatMap(d->d.mesh().stream()).anyMatch(q->q.bone().equals("head")),
                        "Removing provider left the player stuck in the morph");
                });
                // The front camera at z=0 otherwise sits inside the stand we are testing.
                server.runCommand("execute as @a run tp @s -2 99 4 180 0");
                context.waitFor(mc->mc.player.getX()<-1);
                context.runOnClient(mc->mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
                context.waitTicks(4);context.takeScreenshot("native-player-third-person");
                context.runOnClient(mc->mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
                server.runCommand("execute as @a run tp @s 0 99 4 180 0");
                priority(context,id,pack);
                context.runOnClient(mc->BedrockOptionsState.setSelectedIds(List.of()));
                reload(context,false);
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
        } catch(Exception exception) { throw new RuntimeException(exception); }
    }
    /** The source archive stays read-only; test installation lives in the isolated run directory. */
    private static void reference(ClientGameTestContext context,Path source) throws Exception {
        Path game=context.computeOnClient(mc->mc.gameDirectory.toPath());
        Path copy=game.resolve("addons/native-reference.mcpack");
        Files.copy(source,copy,StandardCopyOption.REPLACE_EXISTING);
        var packs=new dev.matanyah.bedrockcompat.bedrock.discovery.BedrockPackRepository(game).scan();
        var pack=packs.stream().filter(p->p.source().equals(copy)).flatMap(p->p.pack().stream()).findFirst().orElseThrow();
        context.runOnClient(mc->BedrockOptionsState.setSelectedIds(List.of(pack.uuid())));
        reload(context,true);
        try(var world=context.worldBuilder().create()) {
            var server=world.getServer();
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
            server.runCommand("execute as @a run tp @s -2 99 4 180 0");
            context.waitFor(mc->mc.player.getX()<-1);
            context.runOnClient(mc->mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(4);context.takeScreenshot("reference-player-third-person");
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
            server.runCommand("item replace entity @e[type=minecraft:armor_stand,limit=1] weapon.mainhand with minecraft:diamond_sword");
            server.runCommand("data merge entity @e[type=minecraft:armor_stand,limit=1] {\"bedrock_compat:pose_index\":3}");
            context.waitFor(mc-> {
                for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand stand)
                    return ((BedrockArmorStandAccess)stand).bedrockCompatState().poseIndex()==3&&!stand.getMainHandItem().isEmpty();
                return false;
            });
            context.runOnClient(mc-> {
                for(var e:mc.level.entitiesForRendering())if(e instanceof ArmorStand stand) {
                    var state=mc.getEntityRenderDispatcher().getRenderer(stand).createRenderState(stand,1);
                    var frame=((NativeRenderState)state).bedrockCompatFrame();
                    check(frame!=null&&!frame.attachments().isEmpty(),"Native hand item was not attached: "+NativeEntityResources.errors());
                }
            });
            context.takeScreenshot("reference-armor-stand-equipped");
            String audit=System.getenv("BEDROCK_REFERENCE_ENTITIES");
            if(audit!=null&&!audit.isBlank()) {
                List<String> missing=new ArrayList<>();
                for(String type:audit.split(",")) {
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
                    server.runCommand("kill @e[type=minecraft:"+type+"]");
                }
                check(missing.isEmpty(),"Reference entities failed: "+missing+"; runtime errors: "+NativeEntityResources.errors().stream()
                    .filter(e->e.startsWith("minecraft:")).toList());
            }
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
        var future=context.computeOnClient(mc-> {
            mc.getResourcePackRepository().reload();
            if(enable)mc.getResourcePackRepository().addPack(VirtualCompatibilityPackProvider.PACK_ID);
            return mc.reloadResourcePacks();
        });
        context.waitFor(mc->future.isDone(),1200);future.join();
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
             "materials":{"default":"armor_stand"},"textures":{"default":"textures/entity/armor_stand"},
             "geometry":{"default":"geometry.test_stand"},"render_controllers":["controller.render.test_stand"]}}}
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
             "geometry":{"default":"geometry.test_boat"},"animations":{"variant":"animation.test.boat"},
             "scripts":{"animate":["variant"]},"render_controllers":["controller.render.test_boat"]}}}
            """);
        Files.writeString(pack.resolve("entity/chest_boat.json"),Files.readString(pack.resolve("entity/boat.json"))
            .replace("minecraft:boat","minecraft:chest_boat"));
        Files.writeString(pack.resolve("entity/minecart.json"),Files.readString(pack.resolve("entity/boat.json"))
            .replace("minecraft:boat","minecraft:minecart").replace("animation.test.boat","animation.test.cart")
            .replace("\"scripts\":{\"animate\":[\"variant\"]}",
                "\"scripts\":{\"pre_animation\":[\"v.hurt=q.hurt_time-q.frame_alpha;\"],\"animate\":[\"variant\"]}"));
        Files.writeString(pack.resolve("models/entity/boat.json"),"""
            {"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.test_boat","texture_width":16,"texture_height":16},
             "bones":[{"name":"hull","cubes":[{"origin":[-10,0,-6],"size":[20,4,12]}]},
             {"name":"variant_marker","cubes":[{"origin":[0,4,0],"size":[1,2,1]}]},
             {"name":"alpha_marker","cubes":[{"origin":[-4,0,0],"size":[1,1,1]}]},
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
             "scripts":{"animate":["arm"],"scale":"q.relative_block_has_any_tag(0,-1,0,'minecraft:base_stone_overworld')?1:2"},
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
              "alpha_marker":{"position":[0,"q.frame_alpha",0]},
              "damage_marker":{"position":["q.hurt_time-q.frame_alpha","q.structural_integrity","q.hurt_direction"]}}}}}
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
             "bones":[{"name":"custom_root","cubes":[{"origin":[-4,0,-4],"size":[8,32,8],"uv":[0,0]}]}]}]}
            """);
        Files.writeString(pack.resolve("render_controllers/stand.json"),"""
            {"render_controllers":{"controller.render.test_stand":{
             "arrays":{"geometries":{"Array.shared":["Array.leaf"],"Array.leaf":["Geometry.default"]},
                       "materials":{"Array.shared":["Array.leaf"],"Array.leaf":["Material.default"]},
                       "textures":{"Array.shared":["Array.leaf"],"Array.leaf":["Texture.default"]}},
             "geometry":"Array.shared[0]","materials":[{"*":"Array.shared[0]"}],
             "textures":["Array.shared[0]"],"part_visibility":[{"*":"!q.is_name_any('hidden')"}]}}}
            """);
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,0xff00ff80);
        ImageIO.write(image,"PNG",pack.resolve("textures/entity/armor_stand.png").toFile());
        Files.createDirectories(pack.resolve("textures/entity/cat"));
        for(String coat:CAT_COATS)Files.copy(pack.resolve("textures/entity/armor_stand.png"),pack.resolve("textures/entity/cat/"+coat+".png"),StandardCopyOption.REPLACE_EXISTING);
        BufferedImage mask=new BufferedImage(32,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<32;x++)mask.setRGB(x,y,x<8?0x00ffffff:x<16?0:x<24?0xffff0000:0x80aabbcc);
        ImageIO.write(mask,"PNG",pack.resolve("textures/entity/test_mask.png").toFile());
    }
}
