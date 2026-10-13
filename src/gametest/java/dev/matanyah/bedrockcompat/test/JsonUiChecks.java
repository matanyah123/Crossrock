package dev.matanyah.bedrockcompat.test;

import dev.matanyah.bedrockcompat.client.ui.json.NativeJsonUi;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;

/** Generated generic JSON UI fixtures; never edits an installed pack. */
final class JsonUiChecks {
    static void fixture(Path pack)throws Exception {
        Files.createDirectories(pack.resolve("ui"));
        Files.writeString(pack.resolve("ui/_ui_defs.json"),"{\"ui_defs\":[\"ui/hud.json\",\"ui/start.json\",\"ui/pause.json\",\"ui/inventory.json\"]}");
        Files.writeString(pack.resolve("ui/_global_variables.json"),"{\"$fixture_color\":[0.3,1,0.4]}");
        Files.createDirectories(pack.resolve("textures/ui"));
        var border=new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<8;y++)for(int x=0;x<8;x++)border.setRGB(x,y,x<2||x>=6||y<2||y>=6?0xffff00ff:0x804080ff);
        javax.imageio.ImageIO.write(border,"png",pack.resolve("textures/ui/compat_border.png").toFile());
        for(var entry:Map.of("hud","hud_screen","start","start_screen","pause","pause_screen","inventory","inventory_screen").entrySet()) {
            Files.writeString(pack.resolve("ui/"+entry.getKey()+".json"),"""
                {"namespace":"NS","fade":{"anim_type":"alpha","duration":1,"from":0.5,"to":1},
                "ROOT@common.empty_panel":{"$width":40,"variables":[{"requires":"($width > 20)","$border":2}],"controls":[
                  {"nine_slice":{"type":"image","texture":"textures/ui/compat_border","nineslice_size":"$border","anchor_from":"top_right","anchor_to":"top_right","offset":[-30,30],"size":["($width * 2)",24],"alpha":0.75,"color":[0.5,1,1]}},
                  {"label":{"type":"label","text":"('UI: ' + #player_name)","visible":"(#hud_visible = true)","anchor_from":"top_left","anchor_to":"top_left",
                     "offset":[8,8],"size":[120,14],"color":"$fixture_color","shadow":true,"anims":["@NS.fade"]}},
                  {"icon":{"type":"image","texture":"textures/ui/heart","uv":[0,0],"uv_size":[16,16],
                     "anchor_from":"top_right","anchor_to":"top_right","offset":[-8,8],"size":[16,16]}},
                  {"clip":{"type":"stack_panel","size":[24,8],"anchor_from":"top_left","anchor_to":"top_left",
                     "offset":[8,26],"clips_children":true,"controls":[{"text":{"type":"label","text":"gui.done","size":[100,12]}}]}}
                ]}}
                """.replace("NS",entry.getKey()).replace("ROOT",entry.getValue()));
        }
        JsonUiInteractionChecks.fixture(pack);
    }
    static void verify(ClientGameTestContext context,java.util.function.Consumer<String> command) {
        context.runOnClient(mc->{
            if(!NativeJsonUi.errors().isEmpty())throw new AssertionError("UI fixture diagnostics: "+NativeJsonUi.errors());
            if(!NativeJsonUi.program().roots().containsKey("inventory.inventory_screen"))throw new AssertionError("Registered UI roots not compiled");
            if(!NativeJsonUi.adapter(new Screen(Component.literal("mod recovery")){}).isEmpty())throw new AssertionError("Modded screen was adapted");
        });
        context.waitFor(mc->NativeJsonUi.renderCount("hud.hud_screen")>0);
        for(String kind:List.of("title","pause","inventory")) {
            if(kind.equals("inventory")){command.accept("gamemode survival @a");context.waitFor(mc->!mc.player.hasInfiniteMaterials());}
            String root=switch(kind){case "title"->"start.start_screen";case "pause"->"pause.pause_screen";default->"inventory.inventory_screen";};
            long before=context.computeOnClient(mc->NativeJsonUi.renderCount(root));
            context.runOnClient(mc->mc.gui.setScreen(switch(kind){case "title"->new TitleScreen();case "pause"->new PauseScreen(true);default->new InventoryScreen(mc.player);}));
            try {context.waitFor(mc->NativeJsonUi.renderCount(root)>before);}
            catch(AssertionError failure){throw new AssertionError("UI adapter "+root+": "+context.computeOnClient(mc->"screen="+mc.gui.screen()+", adapter="+NativeJsonUi.adapter(mc.gui.screen())+", roots="+NativeJsonUi.program().roots().keySet()+", errors="+NativeJsonUi.errors()),failure);}
            context.runOnClient(mc->{if(mc.gui.screen().children().isEmpty())throw new AssertionError("Java screen controls lost");});
            context.takeScreenshot("json-ui-"+kind);
            if(kind.equals("inventory")){
                int original=context.computeOnClient(mc->mc.options.guiScale().get());
                try{for(int scale:List.of(1,2)){
                    long prior=context.computeOnClient(mc->NativeJsonUi.renderCount(root));
                    context.runOnClient(mc->{mc.options.guiScale().set(scale);mc.resizeGui();});
                    context.waitFor(mc->NativeJsonUi.renderCount(root)>prior);
                    context.runOnClient(mc->{if(!NativeJsonUi.errors().isEmpty())throw new AssertionError("Scaled UI diagnostics: "+NativeJsonUi.errors());});
                    context.takeScreenshot("json-ui-inventory-scale-"+scale);
                }}finally{context.runOnClient(mc->{mc.options.guiScale().set(original);mc.resizeGui();});}
            }
            if(kind.equals("pause"))context.runOnClient(mc->{
                if(!mc.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(256,0,0))||mc.gui.screen()!=null)
                    throw new AssertionError("Imported UI intercepted native Escape navigation");
            });
            context.runOnClient(mc->mc.gui.setScreen(null));
            if(kind.equals("inventory")){command.accept("gamemode creative @a");context.waitFor(mc->mc.player.hasInfiniteMaterials());}
        }
        context.runOnClient(mc->{if(NativeJsonUi.color(com.google.gson.JsonParser.parseString("[1,0,0,0.5]"),.5)!=0x40ff0000)throw new AssertionError("UI alpha/tint changed");});
    }
    static void cleared(ClientGameTestContext context) {
        context.runOnClient(mc->{if(!NativeJsonUi.program().roots().isEmpty())throw new AssertionError("UI persisted after pack removal");});
    }
}
