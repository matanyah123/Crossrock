package dev.matanyah.bedrockcompat.client.mixin;

import dev.matanyah.bedrockcompat.client.particle.NativeParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
abstract class NativeParticleEngineMixin {
    @Inject(method="createParticle",at=@At("HEAD"),cancellable=true)
    private void bedrockCompat$replace(ParticleOptions options,double x,double y,double z,double dx,double dy,double dz,CallbackInfoReturnable<Particle> callback) {
        if(NativeParticles.replace(options,x,y,z,dx,dy,dz))callback.setReturnValue(null);
    }
}
