package dev.matanyah.eef.client.mixin;

import dev.matanyah.eef.client.EefVanillaSoundSuppressor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
abstract class SoundManagerMixin {
	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void eef$replaceVanillaDamageSound(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> callback) {
		if (EefVanillaSoundSuppressor.shouldSuppress(sound)) callback.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
	}
}
