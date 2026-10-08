package dev.matanyah.bedrockcompat.client.mixin;

import dev.matanyah.bedrockcompat.client.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class NativeEntityRenderStateMixin implements NativeRenderState {
    @Unique private NativeEntityRenderer.Frame bedrockCompat$frame;
    @Override public NativeEntityRenderer.Frame bedrockCompatFrame(){return bedrockCompat$frame;}
    @Override public void bedrockCompatFrame(NativeEntityRenderer.Frame frame){bedrockCompat$frame=frame;}
}
