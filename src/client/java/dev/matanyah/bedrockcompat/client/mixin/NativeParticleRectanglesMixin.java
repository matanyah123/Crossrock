package dev.matanyah.bedrockcompat.client.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.matanyah.bedrockcompat.client.particle.ParticleRectangles;
import net.minecraft.client.particle.SingleQuadParticle.Layer;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(QuadParticleRenderState.class)
abstract class NativeParticleRectanglesMixin {
    @Inject(method="clear",at=@At("HEAD"))
    private void clearNative(CallbackInfo ci){ParticleRectangles.clear((QuadParticleRenderState)(Object)this);}
    @Inject(method="buildLayer",at=@At("TAIL"))
    private void rectangles(Layer layer,VertexConsumer consumer,CallbackInfo ci){ParticleRectangles.build((QuadParticleRenderState)(Object)this,layer,consumer);}
}
