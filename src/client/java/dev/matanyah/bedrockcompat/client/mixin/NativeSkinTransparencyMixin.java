package dev.matanyah.bedrockcompat.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.matanyah.bedrockcompat.bedrock.entity.PlayerSkinLayout;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkinTextureDownloader.class)
public abstract class NativeSkinTransparencyMixin {
    @Inject(method="setNoAlpha",at=@At("HEAD"),cancellable=true)
    private static void bedrockCompatPreservePadding(NativeImage image,int left,int top,int right,int bottom,CallbackInfo ci) {
        // Java only renders the atlas's cube faces. Bedrock render controllers can
        // also sample its padding: forcing those transparent pixels opaque creates
        // black rectangles in facial overlays. Keep original alpha outside faces.
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)
            if(PlayerSkinLayout.baseTexel(x,y))image.setPixel(x,y,image.getPixel(x,y)|0xff000000);
        ci.cancel();
    }
}
