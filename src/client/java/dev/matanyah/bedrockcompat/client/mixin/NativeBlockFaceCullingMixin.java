package dev.matanyah.bedrockcompat.client.mixin;

import dev.matanyah.bedrockcompat.client.block.NativeBlockModels;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Conservative visible-neighbour fallback; authored Bedrock culling rules are not implemented. */
@Mixin(Block.class)
public abstract class NativeBlockFaceCullingMixin {
    @Inject(method="shouldRenderFace",at=@At("HEAD"),cancellable=true)
    private static void customGeometry(BlockState state,BlockState neighbor,Direction face,CallbackInfoReturnable<Boolean> ci) {
        if(NativeBlockModels.custom(neighbor))ci.setReturnValue(true);
    }
}
