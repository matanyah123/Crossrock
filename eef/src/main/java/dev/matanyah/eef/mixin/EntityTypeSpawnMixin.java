package dev.matanyah.eef.mixin;

import dev.matanyah.eef.EefServerLifecycleBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
abstract class EntityTypeSpawnMixin {
	@Inject(method = "spawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/EntitySpawnReason;ZZ)Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"))
	private void eef$captureSpawn(ServerLevel level, ItemStack stack, LivingEntity source, BlockPos position,
			EntitySpawnReason reason, boolean align, boolean invertY, CallbackInfoReturnable<Entity> callback) {
		Entity result = callback.getReturnValue();
		if (result != null) EefServerLifecycleBridge.send("spawned", result, source, reason.name().toLowerCase(), stack);
	}
}
