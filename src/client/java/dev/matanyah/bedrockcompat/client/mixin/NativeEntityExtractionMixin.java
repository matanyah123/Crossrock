package dev.matanyah.bedrockcompat.client.mixin;

import dev.matanyah.bedrockcompat.client.entity.*;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class NativeEntityExtractionMixin {
    @SuppressWarnings({"rawtypes","unchecked"})
    @Inject(method="createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",at=@At("RETURN"))
    private void bedrockCompat$extract(Entity entity,float partialTick,CallbackInfoReturnable<EntityRenderState> callback) {
        if(NativeEntityRenderer.skipsWorldExtraction())return;
        // Subclass extractRenderState has finished, including texture variants and species fields.
        if(entity instanceof LivingEntity living && callback.getReturnValue() instanceof LivingEntityRenderState state
                && (Object)this instanceof LivingEntityRenderer renderer)
            ((NativeRenderState)state).bedrockCompatFrame(NativeEntityRenderer.extract(living,state,partialTick,renderer.getTextureLocation(state)));
        else if(!(entity instanceof LivingEntity)) {
            var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            boolean tipped=callback.getReturnValue() instanceof net.minecraft.client.renderer.entity.state.TippableArrowRenderState arrow&&arrow.isTipped;
            var texture=net.minecraft.resources.Identifier.parse(
                dev.matanyah.bedrockcompat.bedrock.entity.EntityTextureNames.nonLiving(type.toString(),tipped));
            ((NativeRenderState)callback.getReturnValue()).bedrockCompatFrame(NativeEntityRenderer.extract(entity,callback.getReturnValue(),partialTick,texture));
        }
        // Control-only names must not leave an empty, shadowed nameplate behind.
        var state=callback.getReturnValue();
        if(((NativeRenderState)state).bedrockCompatFrame()!=null
                &&dev.matanyah.bedrockcompat.client.particle.NativeParticles.replacesFire(entity))state.displayFireAnimation=false;
        if(entity instanceof net.minecraft.world.entity.decoration.ArmorStand
                &&((NativeRenderState)state).bedrockCompatFrame()!=null&&entity.hasCustomName()
                &&dev.matanyah.bedrockcompat.entity.ArmorStandDisplayMode.fromName(entity.getCustomName().getString()).hidesName())state.nameTag=null;
    }
}
