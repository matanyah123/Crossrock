package dev.matanyah.bedrockcompat.client.mixin;

import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackProvider;
import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackRuntime;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(PackRepository.class)
abstract class ResourcePackReloadMixin {
    /** Pack.open also serves menu icons; only opening the whole selection is a reload. */
    @Inject(method="openAllSelected",at=@At("HEAD"))
    private void bedrockCompat$prepareReload(CallbackInfoReturnable<List<PackResources>> ci) {
        if(((PackRepository)(Object)this).getSelectedIds().contains(VirtualCompatibilityPackProvider.PACK_ID))
            VirtualCompatibilityPackRuntime.snapshotForReload();
    }
}
