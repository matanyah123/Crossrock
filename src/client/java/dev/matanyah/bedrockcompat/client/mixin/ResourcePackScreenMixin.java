package dev.matanyah.bedrockcompat.client.mixin;

import dev.matanyah.bedrockcompat.client.ui.ResourcePackTabs;
import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackRuntime;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Consumer;

@Mixin(PackSelectionScreen.class)
abstract class ResourcePackScreenMixin extends Screen {
    @Shadow @Final private HeaderAndFooterLayout layout;
    @Shadow @Final private net.minecraft.client.gui.screens.packs.PackSelectionModel model;
    @Unique private boolean crossrock$data;
    @Unique private PackRepository crossrock$repository;
    @Unique private ResourcePackTabs bedrockCompat$session;
    @Unique private MenuTabBar bedrockCompat$tabs;
    protected ResourcePackScreenMixin(Component title){super(title);}
    @ModifyArg(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/packs/PackSelectionModel;<init>(Ljava/util/function/Consumer;Ljava/util/function/Function;Lnet/minecraft/server/packs/repository/PackRepository;Ljava/util/function/Consumer;)V"),index=3)
    private Consumer<PackRepository> bedrockCompat$commitTogether(Consumer<PackRepository> output){
        return repository->{if(dev.matanyah.bedrockcompat.behavior.BehaviorRepositories.isData(repository))output.accept(repository);else VirtualCompatibilityPackRuntime.commitPackScreen(repository,output);};
    }
    @Inject(method="<init>",at=@At("RETURN"))
    private void crossrock$kind(PackRepository repository,Consumer<PackRepository> output,java.nio.file.Path directory,Component title,CallbackInfo ci){crossrock$repository=repository;crossrock$data=dev.matanyah.bedrockcompat.behavior.BehaviorRepositories.isData(repository);}
    @Inject(method="init",at=@At("TAIL"))
    private void bedrockCompat$addTabs(CallbackInfo ci){
        if(crossrock$data){if(bedrockCompat$tabs!=null)removeWidget(bedrockCompat$tabs);bedrockCompat$tabs=addRenderableWidget(dev.matanyah.bedrockcompat.client.behavior.BehaviorPackScreen.bar((PackSelectionScreen)(Object)this,model,crossrock$repository,width,false));return;}
        if(bedrockCompat$session==null)bedrockCompat$session=new ResourcePackTabs((PackSelectionScreen)(Object)this);
        if(bedrockCompat$tabs!=null)removeWidget(bedrockCompat$tabs);
        bedrockCompat$tabs=addRenderableWidget(crossrock$data?dev.matanyah.bedrockcompat.client.behavior.BehaviorPackScreen.bar((PackSelectionScreen)(Object)this,model,crossrock$repository,width,false):bedrockCompat$session.bar(width,false));
    }
    @Inject(method="repositionElements",at=@At("TAIL"))
    private void bedrockCompat$makeTabSpace(CallbackInfo ci){
        for(var child:children()) {
            if(child instanceof AbstractSelectionList<?> list)
                list.updateSizeAndPosition(list.getWidth(),Math.max(24,list.getHeight()-ResourcePackTabs.HEIGHT),list.getX(),list.getY()+ResourcePackTabs.HEIGHT);
            else if(child instanceof AbstractWidget widget&&!(widget instanceof MenuTabBar)&&widget.getY()<layout.getHeaderHeight())
                widget.setY(widget.getY()+ResourcePackTabs.HEIGHT);
        }
        if(bedrockCompat$tabs!=null){
            removeWidget(bedrockCompat$tabs);
            bedrockCompat$tabs=addRenderableWidget(crossrock$data?dev.matanyah.bedrockcompat.client.behavior.BehaviorPackScreen.bar((PackSelectionScreen)(Object)this,model,crossrock$repository,width,false):bedrockCompat$session.bar(width,false));
        }
    }
}
