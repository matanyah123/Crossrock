package dev.matanyah.bedrockcompat.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface NativeJsonUiContainerAccess {
    @Accessor("leftPos") int bedrockUiLeft();
    @Accessor("topPos") int bedrockUiTop();
    @Accessor("imageWidth") int bedrockUiWidth();
    @Accessor("imageHeight") int bedrockUiHeight();
    @Accessor("hoveredSlot") net.minecraft.world.inventory.Slot bedrockUiHovered();
}
