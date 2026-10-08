package dev.matanyah.eef.client.mixin;

import dev.matanyah.eef.client.EefClientInteractions;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Emits a generic EEF placement event from Minecraft's successful placement path. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void eef$afterPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> callback) {
		EefClientInteractions.onBlockItemPlaced(context, callback.getReturnValue());
	}
}
