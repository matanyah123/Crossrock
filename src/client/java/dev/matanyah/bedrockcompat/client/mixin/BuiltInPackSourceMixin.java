package dev.matanyah.bedrockcompat.client.mixin;

import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackProvider;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(BuiltInPackSource.class)
abstract class BuiltInPackSourceMixin {
	@Inject(method = "loadPacks", at = @At("TAIL"))
	private void bedrockCompat$addVirtualPack(Consumer<Pack> consumer, CallbackInfo callbackInfo) {
		if ((Object) this instanceof ClientPackSource) VirtualCompatibilityPackProvider.loadPacks(consumer);
	}
}
