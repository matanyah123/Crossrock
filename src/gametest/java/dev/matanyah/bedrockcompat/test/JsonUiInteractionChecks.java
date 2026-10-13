package dev.matanyah.bedrockcompat.test;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.client.ui.json.*;
import dev.matanyah.bedrockcompat.client.mixin.NativeJsonUiContainerAccess;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Independent, generated fixtures; inventory changes occur only in server setup or native screen input. */
final class JsonUiInteractionChecks {
    static void fixture(Path pack)throws Exception {
        var registration=JsonParser.parseString(Files.readString(pack.resolve("ui/_ui_defs.json"))).getAsJsonObject();
        for(var e:Map.of("chest","chest_screen","crafting","crafting_screen","furnace","furnace_screen","smoker","smoker_screen","blast_furnace","blast_furnace_screen","anvil","anvil_screen","enchanting","enchanting_screen","brewing_stand","brewing_stand_screen","smithing_table","smithing_table_screen","shulker_box","shulker_box_screen").entrySet()) {
            String grid=e.getKey().equals("chest")?"""
              ,{"items":{"type":"grid","collection_name":"container_items","grid_dimensions":[9,3],"grid_item_template":"chest.item","size":[162,54],"offset":[0,-36]}}
              """:"";
            Files.writeString(pack.resolve("ui/"+e.getKey()+".json"),"""
                {"namespace":"NS","item":{"type":"custom","renderer":"inventory_item_renderer","size":[16,16]},
                 "ROOT":{"controls":[
                  {"close":{"type":"button","text":"gui.done","size":[60,18],"anchor_from":"top_left","anchor_to":"top_left","offset":[8,48],
                   "button_mappings":[{"from_button_id":"button.menu_select","to_button_id":"button.menu_exit","mapping_type":"pressed"},{"from_button_id":"button.menu_ok","to_button_id":"button.menu_exit","mapping_type":"focused"}]}}
                  GRID]}}
                """.replace("NS",e.getKey()).replace("ROOT",e.getValue()).replace("GRID",grid));
            registration.getAsJsonArray("ui_defs").add("ui/"+e.getKey()+".json");
        }
        Files.writeString(pack.resolve("ui/_ui_defs.json"),registration.toString());
    }
    static void verify(ClientGameTestContext context,Consumer<Consumer<MinecraftServer>> onServer,Consumer<String> command) {
        command.accept("gamemode survival @a");context.waitFor(mc->!mc.player.hasInfiniteMaterials());
        for(String block:List.of("chest","crafting_table","furnace","smoker","blast_furnace","anvil","enchanting_table","brewing_stand","smithing_table","shulker_box")) {
            command.accept("setblock 1 99 4 minecraft:"+block+" replace");
            onServer.accept(server->{var player=server.getPlayerList().getPlayers().getFirst();var level=player.level();var pos=new BlockPos(1,99,4);var provider=level.getBlockState(pos).getMenuProvider(level,pos);if(provider==null)throw new AssertionError("No real menu provider: "+block);player.openMenu(provider);});
            context.waitFor(mc->mc.gui.screen() instanceof AbstractContainerScreen<?> screen&&!NativeJsonUi.adapter(screen).isEmpty()&&Screens.getWidgets(screen).stream().anyMatch(w->w instanceof NativeJsonUiButton));
            context.runOnClient(mc->{if(!NativeJsonUi.errors().isEmpty())throw new AssertionError("Interaction diagnostics: "+NativeJsonUi.errors());});
            if(block.equals("chest"))chest(context,onServer,true);
            context.takeScreenshot("json-ui-interaction-"+block);
            context.runOnClient(mc->{var screen=mc.gui.screen();var button=Screens.getWidgets(screen).stream().filter(w->w instanceof NativeJsonUiButton).findFirst().orElseThrow();
                if(block.equals("anvil")){screen.setFocused(button);if(!screen.keyPressed(new KeyEvent(257,0,0)))throw new AssertionError("Keyboard activation did not dispatch");}
                else if(!screen.mouseClicked(new MouseButtonEvent(button.getX()+2,button.getY()+2,new MouseButtonInfo(0,0)),false))throw new AssertionError("Mouse activation did not dispatch");
                if(mc.gui.screen()!=null)throw new AssertionError("Native onClose did not close menu");});
            context.waitTicks(4);
            onServer.accept(server->{if(server.getPlayerList().getPlayers().getFirst().containerMenu!=server.getPlayerList().getPlayers().getFirst().inventoryMenu)throw new AssertionError("Close did not synchronize server");});
        }
        command.accept("setblock 1 99 4 minecraft:air");command.accept("gamemode creative @a");context.waitFor(mc->mc.player.hasInfiniteMaterials());
    }
    static void fallback(ClientGameTestContext context,Consumer<Consumer<MinecraftServer>> onServer,Consumer<String> command,String label) {
        command.accept("gamemode survival @a");command.accept("fill -2 98 2 3 98 6 minecraft:stone");command.accept("tp @a 0 99 4");context.waitFor(mc->!mc.player.hasInfiniteMaterials());
        for(String block:List.of("chest","crafting_table","furnace","anvil","enchanting_table","shulker_box","brewing_stand","smithing_table")) {
            command.accept("setblock 1 99 4 minecraft:"+block+" replace");
            onServer.accept(server->{var player=server.getPlayerList().getPlayers().getFirst();var level=player.level();var pos=new BlockPos(1,99,4);player.openMenu(level.getBlockState(pos).getMenuProvider(level,pos));});
            context.waitFor(mc->mc.gui.screen() instanceof AbstractContainerScreen<?>);
            context.waitTicks(2);
            context.runOnClient(mc->{if(Screens.getWidgets(mc.gui.screen()).stream().anyMatch(w->w instanceof NativeJsonUiButton))throw new AssertionError("Fallback unexpectedly installed imported buttons");});
            if(block.equals("chest"))chest(context,onServer,false);
            context.takeScreenshot("json-ui-fallback-"+label+"-"+block);
            context.runOnClient(mc->{if(!mc.gui.screen().keyPressed(new KeyEvent(256,0,0)))throw new AssertionError("Fallback blocked Escape");});
            context.waitFor(mc->mc.gui.screen()==null);
            context.waitTicks(4);
        }
        // Exercise real survival inventory separately; creative has a different Java architecture.
        context.runOnClient(mc->mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));context.waitTicks(2);
        context.takeScreenshot("json-ui-fallback-"+label+"-inventory");context.runOnClient(mc->mc.gui.screen().onClose());
        command.accept("setblock 1 99 4 minecraft:air");command.accept("gamemode creative @a");context.waitFor(mc->mc.player.hasInfiniteMaterials());
    }
    static Runnable prepareReload(ClientGameTestContext context,Consumer<Consumer<MinecraftServer>> onServer,Consumer<String> command) {
        command.accept("gamemode survival @a");command.accept("setblock 1 99 4 minecraft:chest replace");
        onServer.accept(server->{var player=server.getPlayerList().getPlayers().getFirst();var level=player.level();var pos=new BlockPos(1,99,4);player.openMenu(level.getBlockState(pos).getMenuProvider(level,pos));});
        context.waitFor(mc->mc.gui.screen() instanceof AbstractContainerScreen<?> s&&Screens.getWidgets(s).stream().anyMatch(w->w instanceof NativeJsonUiButton));
        var screen=context.computeOnClient(mc->(AbstractContainerScreen<?>)mc.gui.screen());
        var slot=screen.getMenu().slots.getFirst();
        var button=context.computeOnClient(mc->Screens.getWidgets(screen).stream().filter(w->w instanceof NativeJsonUiButton).findFirst().orElseThrow());
        context.runOnClient(mc->screen.setFocused(button));
        return ()->context.runOnClient(mc->{if(screen.getMenu().slots.getFirst()!=slot||slot.y!=18)throw new AssertionError("Reload failed to restore identical native Slot");if(button.active||button.visible||button.keyPressed(new KeyEvent(257,0,0)))throw new AssertionError("Removed button retained keyboard input");if(Screens.getWidgets(screen).contains(button))throw new AssertionError("Reload retained imported widget");if(mc.gui.screen()==screen)screen.onClose();});
    }
    private static void chest(ClientGameTestContext context,Consumer<Consumer<MinecraftServer>> onServer,boolean moved) {
        var baseline=new java.util.concurrent.atomic.AtomicInteger();
        var offhandBaseline=new java.util.concurrent.atomic.AtomicInteger();
        var identities=context.computeOnClient(mc->List.copyOf(((AbstractContainerScreen<?>)mc.gui.screen()).getMenu().slots));
        context.runOnClient(mc->{var screen=(AbstractContainerScreen<?>)mc.gui.screen();var slot=screen.getMenu().slots.getFirst();if(moved?(slot.y<21||slot.y>22):slot.y!=18)throw new AssertionError("Wrong slot presentation: "+slot.y);});
        onServer.accept(server->{var player=server.getPlayerList().getPlayers().getFirst();baseline.set(player.containerMenu.slots.stream().mapToInt(slot->slot.getItem().is(Items.DIAMOND)?slot.getItem().getCount():0).sum());offhandBaseline.set(player.getOffhandItem().is(Items.DIAMOND)?player.getOffhandItem().getCount():0);player.containerMenu.slots.getFirst().set(new ItemStack(Items.DIAMOND,8));player.containerMenu.broadcastChanges();});
        context.waitFor(mc->((AbstractContainerScreen<?>)mc.gui.screen()).getMenu().slots.getFirst().getItem().getCount()==8);
        click(context,0,1,0,false); // Native right-click takes half.
        context.waitFor(mc->((AbstractContainerScreen<?>)mc.gui.screen()).getMenu().getCarried().getCount()==4);
        click(context,1,0,0,false);
        context.waitFor(mc->((AbstractContainerScreen<?>)mc.gui.screen()).getMenu().getCarried().isEmpty());
        context.waitTicks(4);
        onServer.accept(server->{var slots=server.getPlayerList().getPlayers().getFirst().containerMenu.slots;if(slots.getFirst().getItem().getCount()!=4||slots.get(1).getItem().getCount()!=4)throw new AssertionError("Native right/left click did not synchronize: "+slots.getFirst().getItem()+" / "+slots.get(1).getItem());});
        click(context,1,0,1,false); // Shift-click, handled by the unchanged Java handler.
        context.waitFor(mc->((AbstractContainerScreen<?>)mc.gui.screen()).getMenu().slots.get(1).getItem().isEmpty());
        context.waitTicks(4);
        click(context,0,0,0,false);
        click(context,0,0,0,true); // Native double-click collects matching stacks.
        context.waitFor(mc->((AbstractContainerScreen<?>)mc.gui.screen()).getMenu().getCarried().getCount()==baseline.get()+8);
        // Native quick-craft drag distributes the carried stack; no transaction is built by JSON UI.
        context.runOnClient(mc->{var screen=(AbstractContainerScreen<?>)mc.gui.screen();var a=(NativeJsonUiContainerAccess)screen;var first=screen.getMenu().slots.get(3);var second=screen.getMenu().slots.get(4);
            var down=new MouseButtonEvent(a.bedrockUiLeft()+first.x+8,a.bedrockUiTop()+first.y+8,new MouseButtonInfo(0,0));
            var across=new MouseButtonEvent(a.bedrockUiLeft()+second.x+8,a.bedrockUiTop()+second.y+8,new MouseButtonInfo(0,0));
            screen.mouseClicked(down,false);screen.mouseDragged(down,0,0);screen.mouseDragged(across,18,0);screen.mouseReleased(across);});
        context.waitFor(mc->((AbstractContainerScreen<?>)mc.gui.screen()).getMenu().getCarried().isEmpty());
        hover(context,3);
        // AbstractContainerScreen discards checkHotbarKeyPressed's result; assert state, not return value.
        context.runOnClient(mc->mc.gui.screen().keyPressed(new KeyEvent(49,0,0)));
        context.waitTicks(4);
        onServer.accept(server->{if(!server.getPlayerList().getPlayers().getFirst().containerMenu.slots.get(54).getItem().is(Items.DIAMOND))throw new AssertionError("Hotbar swap did not reach server");});
        hover(context,54);
        context.runOnClient(mc->mc.gui.screen().keyPressed(new KeyEvent(70,0,0)));
        context.waitTicks(4);
        context.runOnClient(mc->{var slots=((AbstractContainerScreen<?>)mc.gui.screen()).getMenu().slots;for(int i=0;i<slots.size();i++)if(slots.get(i)!=identities.get(i)||slots.get(i).index!=i)throw new AssertionError("Slot identities changed");});
        onServer.accept(server->{var player=server.getPlayerList().getPlayers().getFirst();int count=player.containerMenu.slots.stream().mapToInt(slot->slot.getItem().is(Items.DIAMOND)?slot.getItem().getCount():0).sum()+(player.getOffhandItem().is(Items.DIAMOND)?player.getOffhandItem().getCount():0);if(count!=baseline.get()+offhandBaseline.get()+8)throw new AssertionError("Inventory lost/duplicated items: "+count+", expected "+(baseline.get()+offhandBaseline.get()+8));if(!player.getOffhandItem().is(Items.DIAMOND))throw new AssertionError("Offhand swap did not reach server");});
    }
    private static void click(ClientGameTestContext context,int index,int button,int modifiers,boolean twice) {
        context.runOnClient(mc->{var screen=(AbstractContainerScreen<?>)mc.gui.screen();var a=(NativeJsonUiContainerAccess)screen;var slot=screen.getMenu().slots.get(index);var event=new MouseButtonEvent(a.bedrockUiLeft()+slot.x+8,a.bedrockUiTop()+slot.y+8,new MouseButtonInfo(button,modifiers));if(!screen.mouseClicked(event,twice))throw new AssertionError("Native slot click not handled");screen.mouseReleased(event);});
    }
    private static void hover(ClientGameTestContext context,int index) {
        var position=context.computeOnClient(mc->{var screen=(AbstractContainerScreen<?>)mc.gui.screen();var a=(NativeJsonUiContainerAccess)screen;var slot=screen.getMenu().slots.get(index);double scale=mc.getWindow().getGuiScale();return new double[]{(a.bedrockUiLeft()+slot.x+8)*scale,(a.bedrockUiTop()+slot.y+8)*scale};});
        context.getInput().setCursorPos(position[0],position[1]);
        // Hover is refreshed during render extraction, not the integrated server's tick loop.
        context.waitFor(mc->{var screen=(AbstractContainerScreen<?>)mc.gui.screen();return ((NativeJsonUiContainerAccess)screen).bedrockUiHovered()==screen.getMenu().slots.get(index);});
    }
}
