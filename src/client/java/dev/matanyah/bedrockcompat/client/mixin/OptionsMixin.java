package dev.matanyah.bedrockcompat.client.mixin;

import dev.matanyah.bedrockcompat.client.BedrockOptionsState;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
abstract class OptionsMixin {
	@Inject(method = "save", at = @At("TAIL"))
	private void bedrockCompat$preserveSelections(CallbackInfo callbackInfo) {
		BedrockOptionsState.persist();
	}
}
