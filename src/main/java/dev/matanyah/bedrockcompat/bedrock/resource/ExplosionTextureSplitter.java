package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

/** Bedrock's sixteen-frame explosion sheet to Java's individually named sprites. */
final class ExplosionTextureSplitter {
    static boolean translate(String path,byte[] bytes,GeneratedResourceSink sink) throws IOException {
        if(!path.equals("textures/particle/explosion.png"))return false;
        BufferedImage sheet=ImageIO.read(new ByteArrayInputStream(bytes));
        if(sheet==null)return false;
        int columns,rows;
        if(sheet.getWidth()==sheet.getHeight()*16){columns=16;rows=1;}
        else if(sheet.getHeight()==sheet.getWidth()*16){columns=1;rows=16;}
        else if(sheet.getWidth()==sheet.getHeight()&&sheet.getWidth()%4==0){columns=4;rows=4;}
        else return false;
        int width=sheet.getWidth()/columns,height=sheet.getHeight()/rows;
        for(int i=0;i<16;i++) {
            var output=new ByteArrayOutputStream();
            ImageIO.write(sheet.getSubimage(i%columns*width,i/columns*height,width,height),"PNG",output);
            sink.put(Identifier.parse("minecraft:textures/particle/explosion_"+i+".png"),output.toByteArray());
        }
        return true;
    }
}
