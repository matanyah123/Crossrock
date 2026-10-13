package dev.matanyah.bedrockcompat.mixin;
import net.minecraft.server.packs.repository.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Set;
@Mixin(PackRepository.class)
public interface BehaviorRepositoryAccess {
    @Accessor("sources")Set<RepositorySource> crossrock$sources();
    @Accessor("sources")@Mutable void crossrock$sources(Set<RepositorySource> sources);
}
