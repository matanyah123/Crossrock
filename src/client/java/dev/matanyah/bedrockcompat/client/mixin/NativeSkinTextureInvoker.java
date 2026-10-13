package dev.matanyah.bedrockcompat.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Allows the client regression fixture to use the real Java skin preprocessing. */
@Mixin(SkinTextureDownloader.class)
public interface NativeSkinTextureInvoker {
    @Invoker("processLegacySkin")
    static NativeImage bedrockCompatProcessSkin(NativeImage image,String source) {throw new AssertionError();}
}
