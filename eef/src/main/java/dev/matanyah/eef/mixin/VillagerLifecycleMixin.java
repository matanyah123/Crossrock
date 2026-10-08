package dev.matanyah.eef.mixin;

import dev.matanyah.eef.EefServerLifecycleBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
abstract class VillagerLifecycleMixin {
	@Inject(method = "pickUpItem", at = @At("HEAD"))
	private void eef$capturePickup(ServerLevel level, ItemEntity item, CallbackInfo callback) {
		EefServerLifecycleBridge.send("item_picked_up", (Villager)(Object)this, item.getOwner(), "pickup", item.getItem());
	}

	@Inject(method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/npc/villager/Villager;", at = @At("RETURN"))
	private void eef$captureBirth(ServerLevel level, AgeableMob otherParent, CallbackInfoReturnable<Villager> callback) {
		Villager child = callback.getReturnValue();
		if (child != null) level.getServer().execute(() ->
			EefServerLifecycleBridge.send("spawned", child, (Entity)(Object)this, "breeding", net.minecraft.world.item.ItemStack.EMPTY));
	}
}
