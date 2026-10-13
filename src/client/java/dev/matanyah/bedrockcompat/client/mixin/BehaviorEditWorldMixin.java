package dev.matanyah.bedrockcompat.client.mixin;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.storage.*;
import net.minecraft.nbt.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.matanyah.bedrockcompat.mixin.BehaviorWorldDataAccess;
@Mixin(EditWorldScreen.class)
public abstract class BehaviorEditWorldMixin extends Screen {
    @Shadow @Final private LinearLayout layout;
    @Shadow @Final private LevelStorageSource.LevelStorageAccess levelAccess;
    protected BehaviorEditWorldMixin(Component title){super(title);}
    @Inject(method="<init>",at=@At("TAIL"))
    private void crossrock$button(CallbackInfo ci){var button=layout.addChild(Button.builder(Component.literal("Datapacks & Behavior Packs"),b->crossrock$open()).width(200).build());addRenderableWidget(button);}
    @Unique private void crossrock$open(){
        try {
            var repository=ServerPacksSource.createPackRepository(levelAccess);repository.reload();
            var data=LevelStorageSource.readDataConfig(levelAccess.getUnfixedDataTagWithFallback());repository.setSelected(data.dataPacks().getEnabled());
            minecraft.setScreenAndShow(new PackSelectionScreen(repository,repo->{
                try {((BehaviorWorldDataAccess)levelAccess).crossrock$update(tag->{
                    CompoundTag packs=new CompoundTag();ListTag enabled=new ListTag();repo.getSelectedIds().forEach(id->enabled.add(StringTag.valueOf(id)));ListTag disabled=new ListTag();repo.getAvailableIds().stream().filter(id->!repo.getSelectedIds().contains(id)).forEach(id->disabled.add(StringTag.valueOf(id)));packs.put("Enabled",enabled);packs.put("Disabled",disabled);tag.put("DataPacks",packs);
                });}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}minecraft.setScreenAndShow(this);
            },levelAccess.getLevelPath(LevelResource.DATAPACK_DIR),Component.literal("World Datapacks")));
        }catch(java.io.IOException e){dev.matanyah.bedrockcompat.BedrockCompat.LOGGER.error("Cannot edit world behavior packs",e);}
    }
}
