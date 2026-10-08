package dev.matanyah.eef.mixin;

import dev.matanyah.eef.EefServerLifecycleBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
abstract class ProjectileHitMixin {
	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void eef$captureProjectileHit(EntityHitResult hit, CallbackInfo callback) {
		Projectile projectile = (Projectile)(Object)this;
		if (!projectile.level().isClientSide()) EefServerLifecycleBridge.send("projectile_hit", hit.getEntity(),
			projectile.getOwner(), BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).toString(), ItemStack.EMPTY);
	}
}
