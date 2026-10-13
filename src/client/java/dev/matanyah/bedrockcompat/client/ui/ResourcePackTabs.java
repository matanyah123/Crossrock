package dev.matanyah.bedrockcompat.client.ui;

import dev.matanyah.bedrockcompat.bedrock.discovery.BedrockPackRepository;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.tabs.*;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;

/** One pack-editing session; navigation never commits either tab. */
public final class ResourcePackTabs {
    public static final int HEIGHT=28;
    private final PackSelectionScreen vanilla;
    private AddonsScreen addons;
    public ResourcePackTabs(PackSelectionScreen vanilla){this.vanilla=vanilla;}
    public void showVanilla(){Minecraft.getInstance().setScreenAndShow(vanilla);}
    public void showAddons(){
        var mc=Minecraft.getInstance();
        if(addons==null)addons=new AddonsScreen(vanilla,new BedrockPackRepository(mc.gameDirectory.toPath()),this);
        mc.setScreenAndShow(addons);
    }
    public void close(){vanilla.onClose();}
    public MenuTabBar bar(int width,boolean modSelected){
        var javaTab=new GridLayoutTab(Component.translatable("bedrock_compat.tab.java"));
        var bedrockTab=new GridLayoutTab(Component.translatable("bedrock_compat.tab.bedrock"));
        var manager=new TabManager(w->{},w->{},tab->{
            if(tab==javaTab&&modSelected)showVanilla();
            else if(tab==bedrockTab&&!modSelected)showAddons();
        },tab->{});
        // Initialize the selected state without navigating during screen init.
        manager.setCurrentTab(modSelected?bedrockTab:javaTab,false,false);
        var bar=MenuTabBar.builder(manager,width).addTabs(javaTab,bedrockTab).build();
        bar.arrangeElements(width);
        return bar;
    }
}
