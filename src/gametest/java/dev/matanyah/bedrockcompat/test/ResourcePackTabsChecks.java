package dev.matanyah.bedrockcompat.test;

import dev.matanyah.bedrockcompat.client.BedrockOptionsState;
import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackRuntime;
import dev.matanyah.bedrockcompat.client.ui.AddonsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.packs.*;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;
import java.util.*;

final class ResourcePackTabsChecks {
    static void run(ClientGameTestContext context) throws Exception {
        // Earlier reload checks can queue a reload during the previous overlay's fade.
        context.waitFor(mc->mc.gui.overlay()==null);
        context.waitTicks(10);
        context.waitFor(mc->mc.gui.overlay()==null);
        var format=net.minecraft.SharedConstants.getCurrentVersion().packVersion(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
        var path=context.computeOnClient(mc->mc.getResourcePackDirectory().resolve("tab-session-test"));
        Files.createDirectories(path);
        Files.writeString(path.resolve("pack.mcmeta"),"{\"pack\":{\"description\":\"Tab session test\",\"min_format\":["+format.major()+","+format.minor()+"],\"max_format\":["+format.major()+","+format.minor()+"]}}");
        var title=context.computeOnClient(mc->new TitleScreen());context.setScreen(()->title);context.waitTicks(2);
        context.runOnClient(mc->check(title.children().stream().noneMatch(w->w instanceof Button b&&b.getMessage().getString().equals("Add-ons")),"Title screen still has Add-ons button"));
        for(boolean javaChange:List.of(false,true)) {
            int[] commits={0};
            var screen=context.computeOnClient(mc->new PackSelectionScreen(mc.getResourcePackRepository(),repo->{
                commits[0]++;mc.options.updateResourcePacks(repo);mc.setScreenAndShow(title);
            },mc.getResourcePackDirectory(),Component.translatable("resourcePack.title")));
            long before=VirtualCompatibilityPackRuntime.reloadGeneration();
            context.setScreen(()->screen);context.waitTicks(2);
            context.runOnClient(mc->{
                check(bar(screen).children().stream().filter(w->w instanceof TabButton b&&b.isSelected()).count()==1,"Tabs have invalid selected state");
                if(javaChange)model(screen).getUnselected().filter(p->p.getId().equals("file/tab-session-test")).findFirst().orElseThrow().select();
                // Source changes and config changes both queue, never apply while navigating.
                VirtualCompatibilityPackRuntime.sourceFilesChanged();
                bar(screen).selectTab(1,false);
            });
            context.waitTicks(2);context.takeScreenshot("pack-tabs-bedrock");
            context.runOnClient(mc->{
                check(mc.gui.screen() instanceof AddonsScreen,"Bedrock tab did not open");
                bar(mc.gui.screen()).selectTab(0,false);
            });
            context.waitTicks(2);context.takeScreenshot("pack-tabs-vanilla");
            context.runOnClient(mc->{
                check(mc.gui.screen()==screen,"Vanilla tab lost its screen/session");
                if(javaChange)check(model(screen).getSelected().anyMatch(p->p.getId().equals("file/tab-session-test")),"Java pack edit was lost switching tabs");
                check(commits[0]==0&&VirtualCompatibilityPackRuntime.reloadGeneration()==before,"Tab navigation triggered resource reload/commit: javaChange="+javaChange+", commits="+commits[0]+", generation="+before+" -> "+VirtualCompatibilityPackRuntime.reloadGeneration());
                bar(screen).selectTab(1,false);
            });
            context.waitTicks(2);
            context.runOnClient(mc->{
                if(javaChange)mc.gui.screen().onClose();
                else mc.gui.screen().children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().equals("Done"))
                    .map(w->(Button)w).findFirst().orElseThrow().onPress(new net.minecraft.client.input.KeyEvent(257,0,0));
            });
            context.waitFor(mc->VirtualCompatibilityPackRuntime.reloadGeneration()>before&&mc.gui.overlay()==null);
            context.waitTicks(5);
            check(commits[0]==1,"Session committed more than once");
            check(VirtualCompatibilityPackRuntime.reloadGeneration()==before+1,"Closing did not trigger exactly one resource reload");
        }
        long unchanged=VirtualCompatibilityPackRuntime.reloadGeneration();
        context.runOnClient(mc->{
            var screen=new PackSelectionScreen(mc.getResourcePackRepository(),repo->{
                mc.options.updateResourcePacks(repo);mc.setScreenAndShow(title);
            },mc.getResourcePackDirectory(),Component.translatable("resourcePack.title"));
            mc.setScreenAndShow(screen);
            screen.onClose();
        });
        context.waitTicks(10);
        check(VirtualCompatibilityPackRuntime.reloadGeneration()==unchanged,"Unchanged pack session reloaded resources");
    }
    private static MenuTabBar bar(net.minecraft.client.gui.screens.Screen screen){return screen.children().stream().filter(MenuTabBar.class::isInstance).map(MenuTabBar.class::cast).findFirst().orElseThrow();}
    private static PackSelectionModel model(PackSelectionScreen screen) {
        try{var f=PackSelectionScreen.class.getDeclaredField("model");f.setAccessible(true);return (PackSelectionModel)f.get(screen);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
