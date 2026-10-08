package dev.matanyah.bedrockcompat.bedrock.resource;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeTgaTest {
    @org.junit.jupiter.api.Test void pngBytesUnderTgaExtensionPassThroughUnmodified() throws Exception {
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var bytes=new java.io.ByteArrayOutputStream();ImageIO.write(image,"PNG",bytes);
        assertArrayEquals(bytes.toByteArray(),VanillaTextureTranslator.decodeTga(bytes.toByteArray()));
    }
    @Test void rlePacketSupportsAlphaAndOriginAndRejectsOverflow() throws Exception {
        byte[] data=new byte[23];data[2]=10;data[12]=2;data[14]=2;data[16]=32;data[17]=0x30;
        data[18]=(byte)0x83;data[19]=0x11;data[20]=0x22;data[21]=0x33;data[22]=(byte)0xff;
        var image=ImageIO.read(new ByteArrayInputStream(VanillaTextureTranslator.decodeTga(data)));
        assertEquals(0xff332211,image.getRGB(0,0));assertEquals(0xff332211,image.getRGB(1,1));
        data[18]=(byte)0x84;assertNull(VanillaTextureTranslator.decodeTga(data));
    }
}
