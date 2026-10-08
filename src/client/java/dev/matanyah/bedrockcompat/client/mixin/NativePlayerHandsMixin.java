package dev.matanyah.bedrockcompat.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.matanyah.bedrockcompat.client.entity.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native camera-space arm pass. Java retains maps and held-item rendering until an attachable owns them. */
@Mixin(ItemInHandRenderer.class)
public abstract class NativePlayerHandsMixin {
    @Unique private boolean bedrockCompat$nativeArms;
    @SuppressWarnings({"rawtypes","unchecked"})
    @Inject(method="submitHandsWithItems",at=@At("HEAD"))
    private void bedrockCompat$hands(float partial,PoseStack poses,SubmitNodeCollector collector,LocalPlayer player,int light,CallbackInfo ci) {
        bedrockCompat$nativeArms=false;
        var minecraft=Minecraft.getInstance();
        if(player.isInvisible()||player.isSpectator()||!minecraft.options.getCameraType().isFirstPerson()
            ||minecraft.getCameraEntity()!=player)return;
        var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        var state=renderer.createRenderState(player,partial);
        var frame=NativeEntityRenderer.extract(player,state,partial,player.getSkin().body().texturePath(),NativeEntityRenderer.View.FIRST_PERSON);
        if(frame==null)return;
        bedrockCompat$nativeArms=true;
        poses.pushPose();
        try {
            poses.translate(0,-player.getEyeHeight(),0);
            poses.scale(-1,-1,1);poses.translate(0,-1.5,0);
            // Items still take their vanilla first-person path; never draw them twice.
            var cameraDraws=frame.draws().stream().map(draw->new NativeEntityRenderer.Draw(draw.texture(),draw.material(),
                dev.matanyah.bedrockcompat.bedrock.entity.BedrockGeometryMesh.reflectDepth(draw.mesh()),draw.appearance())).toList();
            NativeEntityRenderer.submit(new NativeEntityRenderer.Frame(cameraDraws,java.util.List.of(),frame.yaw(),frame.pitch(),frame.scale()),
                poses,collector,light,OverlayTexture.NO_OVERLAY,-1,0);
        }finally{poses.popPose();}
    }
    @Inject(method="submitHandsWithItems",at=@At("RETURN"))
    private void bedrockCompat$end(float partial,PoseStack poses,SubmitNodeCollector collector,LocalPlayer player,int light,CallbackInfo ci) {
        bedrockCompat$nativeArms=false;
    }
    @Inject(method="renderPlayerArm",at=@At("HEAD"),cancellable=true)
    private void bedrockCompat$arm(PoseStack poses,SubmitNodeCollector collector,int light,float equip,float swing,HumanoidArm arm,CallbackInfo ci) {
        if(bedrockCompat$nativeArms)ci.cancel();
    }
    @Inject(method="renderMapHand",at=@At("HEAD"),cancellable=true)
    private void bedrockCompat$mapArm(PoseStack poses,SubmitNodeCollector collector,int light,HumanoidArm arm,CallbackInfo ci) {
        if(bedrockCompat$nativeArms)ci.cancel();
    }
}
