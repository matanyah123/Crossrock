package dev.matanyah.bedrockcompat.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Keep the renderer's normal name-display policy when only its model is replaced. */
@Mixin(EntityRenderer.class)
public interface NativeEntityNameInvoker {
    @Invoker("submitNameDisplay")
    void bedrockCompat$name(EntityRenderState state,PoseStack poses,SubmitNodeCollector collector,CameraRenderState camera);
}
