package dev.matanyah.bedrockcompat.client.mixin;

import dev.matanyah.bedrockcompat.client.entity.NativeRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AvatarRenderer overrides the living layer gate; do not draw Java parts over a native morph. */
@Mixin(AvatarRenderer.class)
public abstract class NativePlayerRendererMixin {
    @Inject(method="shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Z",at=@At("HEAD"),cancellable=true)
    private void bedrockCompat$layers(AvatarRenderState state,CallbackInfoReturnable<Boolean> callback) {
        if(((NativeRenderState)state).bedrockCompatFrame()!=null)callback.setReturnValue(false);
    }
}
