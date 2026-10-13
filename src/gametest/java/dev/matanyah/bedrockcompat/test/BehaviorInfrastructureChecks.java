package dev.matanyah.bedrockcompat.test;

import dev.matanyah.bedrockcompat.behavior.*;
import dev.matanyah.bedrockcompat.client.behavior.*;
import dev.matanyah.bedrockcompat.client.entity.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.*;
import net.minecraft.world.level.storage.LevelResource;
import com.google.gson.JsonPrimitive;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

final class BehaviorInfrastructureChecks {
    static final String A="bd000000-0000-0000-0000-000000000001",AR="bd000000-0000-0000-0000-000000000002",B="bd000000-0000-0000-0000-000000000003",BR="bd000000-0000-0000-0000-000000000004";
    static void run(ClientGameTestContext context)throws Exception {
        Path fixture=context.computeOnClient(mc->mc.gameDirectory.toPath().resolve("behavior-fixtures"));fixture(fixture);
        // Exercise the real datapack factory, vanilla pending model and tab commit.
        Path menu=fixture.resolve("menu/datapacks");copy(fixture.resolve("packs"),menu.resolve("addons"));
        context.runOnClient(mc->{
            var repo=ServerPacksSource.createPackRepository(menu,mc.getLevelSource().getWorldDirValidator());repo.reload();repo.setSelected(List.of("vanilla"));
            check(repo.isAvailable(PackCatalog.PREFIX+A)&&repo.isAvailable(PackCatalog.PREFIX+B),"Individual BP datapacks not discovered");
            var screen=new PackSelectionScreen(repo,r->mc.setScreenAndShow(new TitleScreen()),menu,Component.literal("Datapacks"));mc.setScreenAndShow(screen);
            screen.children().stream().filter(MenuTabBar.class::isInstance).map(MenuTabBar.class::cast).findFirst().orElseThrow().selectTab(1,false);
            check(mc.gui.screen() instanceof BehaviorPackScreen,"Wrong datapack tab");
        });
        context.clickScreenButton("Enable Behavior a");context.clickScreenButton("Enable Behavior b");context.takeScreenshot("behavior-pack-selection");context.clickScreenButton("Done");
        // Real creation-screen datapack reload, not only a directly constructed menu.
        Path global=context.computeOnClient(mc->mc.gameDirectory.toPath().resolve("addons/behavior-tests"));copy(fixture.resolve("packs"),global);
        context.runOnClient(mc->net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(mc,()->mc.setScreenAndShow(new TitleScreen())));
        context.waitForScreen(net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.class);
        context.runOnClient(mc->mc.gui.screen().children().stream().filter(MenuTabBar.class::isInstance).map(MenuTabBar.class::cast).findFirst().orElseThrow().selectTab(2,false));
        context.clickScreenButton("Data Packs");
        context.runOnClient(mc->mc.gui.screen().children().stream().filter(MenuTabBar.class::isInstance).map(MenuTabBar.class::cast).findFirst().orElseThrow().selectTab(1,false));
        context.clickScreenButton("Enable Behavior a");context.clickScreenButton("Enable Behavior b");context.clickScreenButton("Done");
        context.waitForScreen(net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.class);
        context.runOnClient(mc->{var screen=(net.minecraft.client.gui.screens.worldselection.CreateWorldScreen)mc.gui.screen();check(screen.getUiState().getSettings().dataConfiguration().dataPacks().getEnabled().contains(PackCatalog.PREFIX+A),"Creation-screen BP activation was lost");screen.onClose();});
        net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave save;
        UUID[] actorId={null};
        try(var world=context.worldBuilder().create()) {
            save=world.getWorldSave();var server=world.getServer();
            server.runOnServer(s->{check(!BehaviorRuntime.get(s).profile().active(),"New worlds auto-enabled BP");copy(fixture.resolve("packs"),s.getWorldPath(LevelResource.DATAPACK_DIR).resolve("addons"));activate(s,List.of(A,B));});
            ready(context,2);
            server.runCommand("gamemode creative @a");server.runCommand("execute as @a at @s run crossrock summon test:actor_a");server.runCommand("execute as @a run crossrock give test:item_a");
            server.runOnServer(s->{var actor=find(s,"test:actor_a");check(actor!=null,"Generic actor identity did not spawn");actorId[0]=actor.getUUID();actor.property(BehaviorRuntime.get(s).profile().definitions(),"test:value",new JsonPrimitive(7));BehaviorRuntime.get(s).store().put("test:persistence","value",new JsonPrimitive(41));BehaviorRuntime.get(s).save();});
            context.waitFor(mc->mc.level.getEntity(actorId[0]) instanceof BehaviorEntity actor&&actor.clientProperties().get("test:value").getAsInt()==7);
            context.runOnClient(mc->{
                var actor=(BehaviorEntity)mc.level.getEntity(actorId[0]);var renderer=mc.getEntityRenderDispatcher().getRenderer(actor);var state=renderer.createRenderState(actor,0);var frame=((NativeRenderState)state).bedrockCompatFrame();check(frame!=null&&!frame.draws().isEmpty(),"Logical actor did not use RP native geometry: "+NativeEntityResources.errors());
                check(mc.player.getInventory().getItem(0).is(BehaviorIdentities.ITEM),"Custom item not synchronized");
            });
            // A bad preparation must not replace the generation or announce another profile.
            server.runOnServer(s->{long before=BehaviorRuntime.get(s).revision();Path bad=s.getWorldPath(LevelResource.DATAPACK_DIR).resolve("addons/a/entities/actor.json");String original=Files.readString(bad);Files.writeString(bad,"{bad");boolean rejected=false;try{activate(s,List.of(A,B));}catch(Exception expected){rejected=true;}finally{Files.writeString(bad,original);}check(rejected,"Malformed reload was accepted");check(BehaviorRuntime.get(s).revision()==before,"Failed reload replaced profile");activate(s,List.of(B));});
            ready(context,1);
            server.runOnServer(s->check(find(s,"test:actor_a")!=null,"Removal deleted saved actor identity"));
        }
        context.waitFor(mc->!BehaviorClientSession.ready()&&BehaviorClientSession.resources().isEmpty());
        // Offline existing-world editor writes the native level.dat selection under its lock.
        try(var access=context.computeOnClient(mc->mc.getLevelSource().validateAndCreateAccess(save.getSaveDirectory().getFileName().toString()))) {
            check(net.minecraft.world.level.storage.LevelStorageSource.readDataConfig(access.getUnfixedDataTagWithFallback()).dataPacks().getEnabled().contains(PackCatalog.PREFIX+B),"Server selection did not persist before editing");
            context.setScreen(()->{try{return net.minecraft.client.gui.screens.worldselection.EditWorldScreen.create(net.minecraft.client.Minecraft.getInstance(),access,changed->net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new TitleScreen()));}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}});
            context.clickScreenButton("Datapacks & Behavior Packs");
            context.runOnClient(mc->mc.gui.screen().children().stream().filter(MenuTabBar.class::isInstance).map(MenuTabBar.class::cast).findFirst().orElseThrow().selectTab(1,false));
            context.clickScreenButton("Enable Behavior a");context.clickScreenButton("Done");check(net.minecraft.world.level.storage.LevelStorageSource.readDataConfig(access.getUnfixedDataTagWithFallback()).dataPacks().getEnabled().contains(PackCatalog.PREFIX+A),"Offline editor did not persist BP selection");check(net.minecraft.world.level.storage.LevelStorageSource.readDataConfig(access.getUnfixedDataTagWithFallback()).dataPacks().getEnabled().contains(PackCatalog.PREFIX+B),"Offline editor lost previously enabled BP");context.runOnClient(mc->mc.gui.screen().onClose());
        }
        try(var world=save.open()) {
            var server=world.getServer();ready(context,2);
            server.runOnServer(s->{check(BehaviorRuntime.get(s).profile().definitions().find("minecraft:entity","test:actor_b").isPresent(),"World BP selection did not persist");check(BehaviorRuntime.get(s).store().snapshot().getAsJsonObject("test:persistence").get("value").getAsInt()==41,"World state lost on reopen");activate(s,List.of(A,B));});
            ready(context,2);
            server.runOnServer(s->{var actor=find(s,"test:actor_a");check(actor!=null&&actor.clientProperties().get("test:value").getAsInt()==7,"Dormant actor state lost on load/re-enable");activate(s,List.of());});
            ready(context,0);server.runOnServer(s->check(!BehaviorRuntime.get(s).profile().active(),"Pack removal left server definitions"));
        }
        // Separate dedicated-server owner, real multiplayer transport and a reconnect.
        if(System.getenv("CROSSROCK_TEST_EULA_ACCEPTED")==null)throw new IllegalStateException("Dedicated test requires explicit EULA acceptance");
        Files.writeString(Path.of("eula.txt"),"# User accepted the EULA for this isolated local test server\neula=true\n");
        Properties serverProperties=new Properties();serverProperties.setProperty("server-ip","127.0.0.1");
        try(var server=context.worldBuilder().createServer(serverProperties)) {
            server.runOnServer(s->{copy(fixture.resolve("packs"),s.getWorldPath(LevelResource.DATAPACK_DIR).resolve("addons"));activate(s,List.of(A,B));});
            try(var connection=server.connect()){ready(context,2);server.runOnServer(s->check(BehaviorNetworking.accepted(s.getPlayerList().getPlayers().getFirst()),"Dedicated negotiation not acknowledged"));}
            context.waitFor(mc->BehaviorClientSession.resources().isEmpty());
            try(var connection=server.connect()){ready(context,2);server.runOnServer(s->activate(s,List.of()));ready(context,0);}
        }
        context.waitFor(mc->BehaviorClientSession.resources().isEmpty());
    }
    static void ready(ClientGameTestContext context,int resources){context.waitFor(mc->BehaviorClientSession.ready()&&BehaviorClientSession.resources().size()==resources&&mc.gui.overlay()==null,2400);}
    static BehaviorEntity find(net.minecraft.server.MinecraftServer s,String id){for(var level:s.getAllLevels())for(var e:level.getAllEntities())if(e instanceof BehaviorEntity a&&a.bedrockIdentifier().equals(id))return a;return null;}
    static void activate(net.minecraft.server.MinecraftServer server,List<String> ids){var repo=server.getPackRepository();repo.reload();List<String> selected=new ArrayList<>(repo.getSelectedIds().stream().filter(id->!id.startsWith(PackCatalog.PREFIX)).toList());for(var id:ids)selected.add(PackCatalog.PREFIX+id);server.reloadResources(selected).join();}
    static void copy(Path from,Path to)throws java.io.IOException{try(var files=Files.walk(from)){for(var source:files.toList()){var target=to.resolve(from.relativize(source));if(Files.isDirectory(source))Files.createDirectories(target);else Files.copy(source,target,StandardCopyOption.REPLACE_EXISTING);}}}
    static void fixture(Path root)throws Exception {
        for(String name:List.of("a","b")) {
            String bp=name.equals("a")?A:B,rp=name.equals("a")?AR:BR;Path pack=root.resolve("packs/"+name),resource=root.resolve("packs/"+name+"-rp");Files.createDirectories(pack.resolve("entities"));Files.createDirectories(pack.resolve("items"));Files.createDirectories(resource.resolve("entity"));Files.createDirectories(resource.resolve("render_controllers"));Files.createDirectories(resource.resolve("models/entity"));Files.createDirectories(resource.resolve("textures/items"));Files.createDirectories(resource.resolve("textures/entity"));
            Files.writeString(pack.resolve("manifest.json"),manifest(bp,rp,"data","Behavior "+name));Files.writeString(resource.resolve("manifest.json"),manifest(rp,bp,"resources","Resource "+name));
            Files.writeString(pack.resolve("entities/actor.json"),"{\"minecraft:entity\":{\"description\":{\"identifier\":\"test:actor_"+name+"\",\"properties\":{\"test:value\":{\"type\":\"int\",\"default\":1,\"range\":[0,10],\"client_sync\":true}}},\"components\":{}}}");
            Files.writeString(pack.resolve("items/item.json"),"{\"minecraft:item\":{\"description\":{\"identifier\":\"test:item_"+name+"\"},\"components\":{\"minecraft:icon\":\"item_"+name+"\"}}}");
            Files.writeString(resource.resolve("render_controllers/default.json"),"{\"render_controllers\":{\"controller.render.default\":{\"geometry\":\"Geometry.default\",\"materials\":[{\"*\":\"Material.default\"}],\"textures\":[\"Texture.default\"]}}}");
            Files.writeString(resource.resolve("textures/item_texture.json"),"{\"texture_data\":{\"item_"+name+"\":{\"textures\":\"textures/items/item\"}}}");
            Files.writeString(resource.resolve("entity/actor.json"),"{\"minecraft:client_entity\":{\"description\":{\"identifier\":\"test:actor_"+name+"\",\"materials\":{\"default\":\"entity_alphatest\"},\"textures\":{\"default\":\"textures/entity/actor\"},\"geometry\":{\"default\":\"geometry.test_"+name+"\"},\"render_controllers\":[\"controller.render.default\"]}}}");
            Files.writeString(resource.resolve("models/entity/actor.json"),"{\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.test_"+name+"\",\"texture_width\":16,\"texture_height\":16},\"bones\":[{\"name\":\"root\",\"cubes\":[{\"origin\":[-4,0,-4],\"size\":[8,16,8],\"uv\":[0,0]}]}]}]}");
            BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);for(int x=0;x<16;x++)for(int y=0;y<16;y++)image.setRGB(x,y,0xff22aa88);ImageIO.write(image,"png",resource.resolve("textures/entity/actor.png").toFile());ImageIO.write(image,"png",resource.resolve("textures/items/item.png").toFile());ImageIO.write(image,"png",pack.resolve("pack_icon.png").toFile());
        }
    }
    static String manifest(String uuid,String dependency,String type,String name){return "{\"format_version\":2,\"header\":{\"name\":\""+name+"\",\"description\":\"Infrastructure test\",\"uuid\":\""+uuid+"\",\"version\":[1,0,0]},\"modules\":[{\"type\":\""+type+"\",\"uuid\":\"bd000000-0000-0000-0000-000000000009\",\"version\":[1,0,0]}],\"dependencies\":[{\"uuid\":\""+dependency+"\",\"version\":[1,0,0]}]}";}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
