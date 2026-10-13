package dev.matanyah.bedrockcompat.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.matanyah.bedrockcompat.client.entity.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Evaluate after the inventory has supplied its mouse-following rotations. */
@Mixin(InventoryScreen.class)
public abstract class NativeInventoryEntityMixin {
    @WrapOperation(method="extractEntityInInventoryFollowsMouse",at=@At(value="INVOKE",
        target="Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
    private static EntityRenderState crossrock$state(LivingEntity entity,Operation<EntityRenderState> original) {
        return NativeEntityRenderer.withoutWorldExtraction(()->original.call(entity));
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    @WrapOperation(method="extractEntityInInventoryFollowsMouse",at=@At(value="INVOKE",
        target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;entity(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FLorg/joml/Vector3fc;Lorg/joml/Quaternionfc;Lorg/joml/Quaternionfc;IIII)V"))
    private static void crossrock$preview(GuiGraphicsExtractor graphics,EntityRenderState state,float scale,
            Vector3fc offset,Quaternionfc rotation,Quaternionfc camera,int x1,int y1,int x2,int y2,
            Operation<Void> original,@Local(argsOnly=true) LivingEntity entity) {
        if(state instanceof LivingEntityRenderState living&&Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity)
                instanceof LivingEntityRenderer renderer)
            ((NativeRenderState)state).bedrockCompatFrame(NativeEntityRenderer.extract(entity,living,1,
                renderer.getTextureLocation(living),NativeEntityRenderer.View.UI));
        original.call(graphics,state,scale,offset,rotation,camera,x1,y1,x2,y2);
    }
}
