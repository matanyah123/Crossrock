package dev.matanyah.bedrockcompat.client.mixin;
import dev.matanyah.bedrockcompat.behavior.*;
import dev.matanyah.bedrockcompat.client.behavior.BehaviorPackScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.stream.Stream;
@Mixin(PackSelectionModel.class)
public abstract class BehaviorPackModelMixin {
    @Shadow @Final private PackRepository repository;
    @Inject(method={"getSelected","getUnselected"},at=@At("RETURN"),cancellable=true)
    private void crossrock$separate(CallbackInfoReturnable<Stream<PackSelectionModel.Entry>> ci){if(BehaviorRepositories.isData(repository)&&!BehaviorPackScreen.UNFILTERED.get())ci.setReturnValue(ci.getReturnValue().filter(e->!e.getId().startsWith(PackCatalog.PREFIX)));}
}
