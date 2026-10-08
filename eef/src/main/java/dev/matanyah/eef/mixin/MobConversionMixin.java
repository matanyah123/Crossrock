package dev.matanyah.eef.mixin;

import dev.matanyah.eef.EefServerLifecycleBridge;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
abstract class MobConversionMixin {
	@Inject(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;", at = @At("RETURN"))
	private <T extends Mob> void eef$captureConversion(EntityType<T> type, ConversionParams params,
			EntitySpawnReason reason, ConversionParams.AfterConversion<T> after, CallbackInfoReturnable<T> callback) {
		T result = callback.getReturnValue();
		if (result != null) EefServerLifecycleBridge.send("transformed", result, (Mob)(Object)this,
			reason.name().toLowerCase(), ItemStack.EMPTY);
	}
}
