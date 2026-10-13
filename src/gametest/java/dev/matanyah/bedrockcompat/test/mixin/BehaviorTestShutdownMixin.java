package dev.matanyah.bedrockcompat.test.mixin;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Fabric 0.159's test phaser cannot service 26.2's new blocking pre-halt task.
 * Only the test mod uses this shim; production halt and persistence are unchanged.
 */
@Mixin(IntegratedServer.class)
public abstract class BehaviorTestShutdownMixin {
    @Redirect(method="halt",at=@At(value="INVOKE",target="Lnet/minecraft/client/server/IntegratedServer;executeBlocking(Ljava/lang/Runnable;)V"))
    private void crossrock$testHalt(IntegratedServer server,Runnable cleanup){server.execute(cleanup);}
}
