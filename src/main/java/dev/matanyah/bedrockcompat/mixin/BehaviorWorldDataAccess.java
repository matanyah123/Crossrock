package dev.matanyah.bedrockcompat.mixin;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.function.Consumer;
import java.io.IOException;
@Mixin(LevelStorageSource.LevelStorageAccess.class)
public interface BehaviorWorldDataAccess {
    @Invoker("modifyLevelDataWithoutDatafix")void crossrock$update(Consumer<CompoundTag> update)throws IOException;
}
