package dev.matanyah.bedrockcompat.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.matanyah.bedrockcompat.client.entity.*;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class NativeLivingEntityRendererMixin {
    @WrapOperation(method="submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V"))
    private void bedrockCompat$rotations(LivingEntityRenderer<?,?,?> renderer,LivingEntityRenderState state,PoseStack poses,
            float yaw,float scale,Operation<Void> original) {
        if(((NativeRenderState)state).bedrockCompatFrame()==null)original.call(renderer,state,poses,yaw,scale);
        else {
            // Bedrock clips own sleep/death/swim/roll transforms; applying Java's
            // species-specific transforms as well rotates/translates the model twice.
            float facing=state.bedOrientation==null?yaw:state.bedOrientation.toYRot();
            poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-facing));
        }
    }
    @WrapOperation(method="submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",ordinal=0))
    private void bedrockCompat$sleepOffset(PoseStack poses,float x,float y,float z,Operation<Void> original,
            LivingEntityRenderState state) {
        if(((NativeRenderState)state).bedrockCompatFrame()==null)original.call(poses,x,y,z);
    }
    @WrapOperation(method="submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;scale(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void bedrockCompat$scale(LivingEntityRenderer<?,?,?> renderer,LivingEntityRenderState state,PoseStack poses,Operation<Void> original) {
        if(((NativeRenderState)state).bedrockCompatFrame()==null)original.call(renderer,state,poses);
    }
    @WrapOperation(method="submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private void bedrockCompat$submit(SubmitNodeCollector collector,Model<?> model,Object value,PoseStack poses,RenderType type,
            int light,int overlay,int color,TextureAtlasSprite sprite,int outline,ModelFeatureRenderer.CrumblingOverlay crumble,Operation<Void> original) {
        var frame=((NativeRenderState)value).bedrockCompatFrame();
        if(frame==null)original.call(collector,model,value,poses,type,light,overlay,color,sprite,outline,crumble);
        else {
            var state=(LivingEntityRenderState)value;
            NativeEntityRenderer.submit(frame,poses,collector,light,overlay,color,outline,state.isInvisible&&state.isInvisibleToPlayer);
        }
    }
    @Inject(method="shouldRenderLayers",at=@At("HEAD"),cancellable=true)
    private void bedrockCompat$layers(LivingEntityRenderState state,CallbackInfoReturnable<Boolean> cir) {
        if(((NativeRenderState)state).bedrockCompatFrame()!=null)cir.setReturnValue(false);
    }
}
