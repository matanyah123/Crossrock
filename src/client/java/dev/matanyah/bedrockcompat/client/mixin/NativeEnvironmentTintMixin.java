package dev.matanyah.bedrockcompat.client.mixin;

import dev.matanyah.bedrockcompat.client.environment.NativeEnvironment;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public abstract class NativeEnvironmentTintMixin {
    @Unique
    private void override(String kind, CallbackInfoReturnable<Integer> ci) {
        Integer color = NativeEnvironment.tint((Biome)(Object)this, kind);
        if (color != null) {
            // Java 26.2 consumes ARGB tints, including fluid vertex opacity.
            // Bedrock surface_color supplies RGB only; retain Java's alpha.
            ci.setReturnValue((ci.getReturnValueI() & 0xff000000) | (color & 0x00ffffff));
        }
    }
    @Inject(method="getGrassColor",at=@At("RETURN"),cancellable=true)
    private void grass(double x,double z,CallbackInfoReturnable<Integer> ci){override("grass",ci);}
    @Inject(method="getFoliageColor",at=@At("RETURN"),cancellable=true)
    private void foliage(CallbackInfoReturnable<Integer> ci){override("foliage",ci);}
    @Inject(method="getDryFoliageColor",at=@At("RETURN"),cancellable=true)
    private void dry(CallbackInfoReturnable<Integer> ci){override("dry_foliage",ci);}
    @Inject(method="getWaterColor",at=@At("RETURN"),cancellable=true)
    private void water(CallbackInfoReturnable<Integer> ci){override("water",ci);}
}
