package dev.matanyah.eef.mixin;

import dev.matanyah.eef.EefServerTradeBridge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantResultSlot.class)
abstract class MerchantResultSlotMixin {
	@Shadow @Final private MerchantContainer slots;
	@Shadow @Final private Merchant merchant;
	@Inject(method = "onTake", at = @At("HEAD"))
	private void eef$captureCompletedTrade(Player player, ItemStack result, CallbackInfo callback) {
		MerchantOffer offer = slots.getActiveOffer();
		if (offer != null) EefServerTradeBridge.capture(merchant, player, offer, result);
	}
}
