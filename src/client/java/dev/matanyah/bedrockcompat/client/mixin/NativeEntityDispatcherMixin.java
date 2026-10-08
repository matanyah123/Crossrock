package dev.matanyah.bedrockcompat.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.matanyah.bedrockcompat.client.entity.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class NativeEntityDispatcherMixin {
    @WrapOperation(method="submit",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void bedrockCompat$nonLiving(EntityRenderer<?,?> renderer,EntityRenderState state,PoseStack poses,
            SubmitNodeCollector collector,CameraRenderState camera,Operation<Void> original) {
        var frame=((NativeRenderState)state).bedrockCompatFrame();
        if(frame==null||state instanceof LivingEntityRenderState) {
            original.call(renderer,state,poses,collector,camera);return;
        }
        if(!state.isInvisible||state.appearsGlowing()) {
            poses.pushPose();
            poses.mulPose(new org.joml.Quaternionf().rotationY((float)Math.toRadians(180-frame.yaw())));
            poses.scale(-1,-1,1);poses.translate(0,-1.5,0);
            NativeEntityRenderer.submit(frame,poses,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,-1,state.outlineColor,state.isInvisible);
            poses.popPose();
        }
        if(state.leashStates!=null)for(var leash:state.leashStates)collector.submitLeash(poses,leash);
        ((NativeEntityNameInvoker)renderer).bedrockCompat$name(state,poses,collector,camera);
        // Dispatcher-owned fire and shadow submissions still execute after this operation.
    }
}
