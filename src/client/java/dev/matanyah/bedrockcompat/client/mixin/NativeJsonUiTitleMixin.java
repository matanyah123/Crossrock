package dev.matanyah.bedrockcompat.client.mixin;

import dev.matanyah.bedrockcompat.client.ui.json.NativeJsonUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TitleScreen draws its own panorama rather than Screen's background event. */
@Mixin(TitleScreen.class)
public abstract class NativeJsonUiTitleMixin {
    @Inject(method="extractRenderState",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void crossrock$jsonUi(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta,CallbackInfo ci) {
        NativeJsonUi.render((TitleScreen)(Object)this,graphics);
    }
}
