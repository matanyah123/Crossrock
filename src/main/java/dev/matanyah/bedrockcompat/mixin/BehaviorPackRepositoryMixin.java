package dev.matanyah.bedrockcompat.mixin;
import dev.matanyah.bedrockcompat.behavior.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.file.Path;
import java.util.LinkedHashSet;
@Mixin(ServerPacksSource.class)
public abstract class BehaviorPackRepositoryMixin {
    @Inject(method="createPackRepository(Ljava/nio/file/Path;Lnet/minecraft/world/level/validation/DirectoryValidator;)Lnet/minecraft/server/packs/repository/PackRepository;",at=@At("RETURN"))
    private static void crossrock$mark(Path path,DirectoryValidator validator,CallbackInfoReturnable<PackRepository> ci){
        var repository=ci.getReturnValue();var access=(BehaviorRepositoryAccess)repository;
        var sources=new LinkedHashSet<>(access.crossrock$sources());sources.add(new BehaviorPackSource(path));access.crossrock$sources(sources);
        BehaviorRepositories.mark(repository,path);
    }
}
