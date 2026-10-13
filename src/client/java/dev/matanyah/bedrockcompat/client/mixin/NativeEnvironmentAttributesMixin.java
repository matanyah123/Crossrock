package dev.matanyah.bedrockcompat.client.mixin;

import dev.matanyah.bedrockcompat.client.environment.NativeEnvironment;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnvironmentAttributeSystem.class)
public abstract class NativeEnvironmentAttributesMixin {
    @Inject(method="addDefaultLayers",at=@At(value="INVOKE",target="Lnet/minecraft/world/attribute/EnvironmentAttributeSystem;addBiomeLayer(Lnet/minecraft/world/attribute/EnvironmentAttributeSystem$Builder;Lnet/minecraft/core/HolderLookup;Lnet/minecraft/world/level/biome/BiomeManager;)V",shift=At.Shift.AFTER))
    private static void bedrockEnvironmentLayers(EnvironmentAttributeSystem.Builder builder,Level level,CallbackInfo ci){NativeEnvironment.addLayers(builder,level);}
}
