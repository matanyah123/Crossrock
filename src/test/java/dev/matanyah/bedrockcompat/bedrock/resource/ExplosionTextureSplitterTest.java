package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.resource.InMemoryGeneratedResourceStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class ExplosionTextureSplitterTest {
    @Test void everySupportedSheetLayoutPreservesFrameOrderAndPixels() throws Exception {
        for(int columns:new int[]{16,1,4}) {
            var sheet=new BufferedImage(columns*2,16/columns*2,BufferedImage.TYPE_INT_ARGB);
            for(int i=0;i<16;i++)for(int x=0;x<2;x++)for(int y=0;y<2;y++)
                sheet.setRGB(i%columns*2+x,i/columns*2+y,0xff000000|i*123456);
            var bytes=new ByteArrayOutputStream();ImageIO.write(sheet,"PNG",bytes);
            var store=new InMemoryGeneratedResourceStore();
            assertTrue(ExplosionTextureSplitter.translate("textures/particle/explosion.png",bytes.toByteArray(),store));
            for(int i=0;i<16;i++) {
                var frame=ImageIO.read(new ByteArrayInputStream(store.snapshot().resources().get(
                    Identifier.parse("minecraft:textures/particle/explosion_"+i+".png"))));
                assertEquals(2,frame.getWidth());assertEquals(2,frame.getHeight());
                assertEquals(0xff000000|i*123456,frame.getRGB(1,1));
            }
        }
    }
}
