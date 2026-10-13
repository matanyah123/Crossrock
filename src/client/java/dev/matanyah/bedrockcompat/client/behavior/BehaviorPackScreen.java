package dev.matanyah.bedrockcompat.client.behavior;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.tabs.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import dev.matanyah.bedrockcompat.behavior.PackCatalog;
import java.util.*;
import java.util.function.Supplier;

/** Uses vanilla's pending selection model: switching tabs never commits or reloads a world. */
public final class BehaviorPackScreen extends Screen {
    public static final ThreadLocal<Boolean> UNFILTERED=ThreadLocal.withInitial(()->false);
    private final PackSelectionScreen vanilla;private final PackSelectionModel model;private final net.minecraft.server.packs.repository.PackRepository repository;private int page;private String error="";
    private List<PackSelectionModel.Entry> entries=List.of();
    public BehaviorPackScreen(PackSelectionScreen vanilla,PackSelectionModel model,net.minecraft.server.packs.repository.PackRepository repository){super(Component.literal("Bedrock Behavior Packs"));this.vanilla=vanilla;this.model=model;this.repository=repository;}
    public static <T>T all(Supplier<T> read){boolean before=UNFILTERED.get();UNFILTERED.set(true);try{return read.get();}finally{UNFILTERED.set(before);}}
    public static MenuTabBar bar(PackSelectionScreen vanilla,PackSelectionModel model,net.minecraft.server.packs.repository.PackRepository repository,int width,boolean bedrock){
        var javaTab=new GridLayoutTab(Component.literal("Java Datapacks"));var bpTab=new GridLayoutTab(Component.literal("Bedrock Behavior Packs"));
        var manager=new TabManager(w->{},w->{},tab->{if(tab==javaTab&&bedrock)Minecraft.getInstance().setScreenAndShow(vanilla);else if(tab==bpTab&&!bedrock)Minecraft.getInstance().setScreenAndShow(new BehaviorPackScreen(vanilla,model,repository));},tab->{});
        manager.setCurrentTab(bedrock?bpTab:javaTab,false,false);var bar=MenuTabBar.builder(manager,width).addTabs(javaTab,bpTab).build();bar.arrangeElements(width);return bar;
    }
    @Override protected void init(){
        entries=all(()->java.util.stream.Stream.concat(model.getSelected(),model.getUnselected()).filter(e->e.getId().startsWith(PackCatalog.PREFIX)).toList());
        Set<String> selected=all(()->model.getSelected().map(PackSelectionModel.Entry::getId).collect(java.util.stream.Collectors.toSet()));
        int count=Math.max(1,(height-125)/36);page=Math.min(page,Math.max(0,(entries.size()-1)/count));
        addRenderableWidget(bar(vanilla,model,repository,width,true));
        for(int i=page*count;i<Math.min(entries.size(),(page+1)*count);i++) {
            var entry=entries.get(i);boolean enabled=selected.contains(entry.getId());
            var button=addRenderableWidget(Button.builder(Component.literal((enabled?"Disable ":"Enable ")+entry.getTitle().getString()),b->{toggle(entry,enabled);rebuildWidgets();}).bounds(width/2-120,48+(i-page*count)*36,240,28).build());
            button.setTooltip(Tooltip.create(entry.getDescription()));
            if(enabled){int y=48+(i-page*count)*36;var up=addRenderableWidget(Button.builder(Component.literal("↑"),b->{entry.moveUp();rebuildWidgets();}).bounds(width/2+124,y,20,14).build());up.active=entry.canMoveUp();var down=addRenderableWidget(Button.builder(Component.literal("↓"),b->{entry.moveDown();rebuildWidgets();}).bounds(width/2+124,y+14,20,14).build());down.active=entry.canMoveDown();}
        }
        addRenderableWidget(Button.builder(Component.literal("Previous"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(width/2-154,height-65,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"),b->{page=Math.min(Math.max(0,(entries.size()-1)/count),page+1);rebuildWidgets();}).bounds(width/2+54,height-65,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(width/2-50,height-35,100,20).build());
    }
    private void toggle(PackSelectionModel.Entry entry,boolean enabled){
        try {
            var catalog=PackCatalog.discover(Minecraft.getInstance().gameDirectory.toPath(),dev.matanyah.bedrockcompat.behavior.BehaviorRepositories.path(repository));
            String uuid=entry.getId().substring(PackCatalog.PREFIX.length());
            if(enabled){for(var other:all(()->model.getSelected().toList()))if(!other.getId().equals(entry.getId())&&other.getId().startsWith(PackCatalog.PREFIX)){var root=catalog.require(other.getId().substring(PackCatalog.PREFIX.length()));if(catalog.closure(List.of(root)).stream().anyMatch(p->p.uuid().equals(uuid)))throw new IllegalArgumentException("Disable dependent pack first: "+other.getTitle().getString());}entry.unselect();}
            else {var required=catalog.closure(List.of(catalog.require(uuid))).stream().filter(PackCatalog::behavior).map(PackCatalog::datapackId).collect(java.util.stream.Collectors.toSet());for(var candidate:all(()->model.getUnselected().toList()))if(required.contains(candidate.getId()))candidate.select();}
            error="";
        }catch(RuntimeException failure){error=failure.getMessage();}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){super.extractRenderState(g,x,y,delta);if(!error.isBlank())g.centeredText(font,Component.literal(error),width/2,height-90,0xffff5555);int count=Math.max(1,(height-125)/36);for(int i=page*count;i<Math.min(entries.size(),(page+1)*count);i++)g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,entries.get(i).getIconTexture(),width/2-152,48+(i-page*count)*36,0,0,28,28,28,28);if(entries.isEmpty())g.centeredText(font,Component.literal("Place .mcpack / .mcaddon packs in addons/"),width/2,60,0xffffffff);}
    @Override public void onFilesDrop(List<java.nio.file.Path> paths){
        for(var path:paths)try{dev.matanyah.bedrockcompat.behavior.BedrockPackImporter.copy(path,dev.matanyah.bedrockcompat.behavior.BehaviorRepositories.path(repository).resolve("addons"));error="";}catch(java.io.IOException failure){error=failure.getMessage();}
        model.findNewPacks();rebuildWidgets();
    }
    @Override public void onClose(){vanilla.onClose();}
}
