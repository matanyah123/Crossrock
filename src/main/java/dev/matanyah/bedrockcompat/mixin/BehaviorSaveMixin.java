package dev.matanyah.bedrockcompat.mixin;
import dev.matanyah.bedrockcompat.behavior.BehaviorRuntime;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MinecraftServer.class)
public abstract class BehaviorSaveMixin {
    @Inject(method="saveEverything",at=@At("RETURN"))
    private void crossrock$save(boolean suppressLogs,boolean flush,boolean forced,CallbackInfoReturnable<Boolean> ci){BehaviorRuntime.get((MinecraftServer)(Object)this).save();}
}
