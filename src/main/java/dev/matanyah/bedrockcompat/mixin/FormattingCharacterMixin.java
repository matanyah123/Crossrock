package dev.matanyah.bedrockcompat.mixin;

import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allow typing/pasting legacy formatting, without relaxing control-character checks. */
@Mixin(StringUtil.class)
public abstract class FormattingCharacterMixin {
    @Inject(method="isAllowedChatCharacter",at=@At("HEAD"),cancellable=true)
    private static void bedrockCompat$formatting(int codepoint,CallbackInfoReturnable<Boolean> cir) {
        if(codepoint==0xA7)cir.setReturnValue(true);
    }
}
