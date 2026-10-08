package dev.matanyah.bedrockcompat.client.entity;

import com.mojang.blaze3d.platform.NativeImage;
import dev.matanyah.bedrockcompat.bedrock.entity.BedrockTextureComposition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.util.*;

/** Render-thread cache. Composites belong to one resource snapshot, never a source pack. */
final class NativeEntityTextures {
    private record Pair(Identifier base,Identifier mask) {}
    private static final Map<Pair,Identifier> composites=new LinkedHashMap<>();
    private static final Map<Pair,String> failures=new LinkedHashMap<>();
    private static long pixels;
    private static long generation;
    private NativeEntityTextures() {}

    static void reset() {
        var manager=Minecraft.getInstance().getTextureManager();
        composites.values().forEach(manager::release);
        composites.clear();failures.clear();pixels=0;generation++;
    }

    static Identifier masked(Identifier base,Identifier mask) {
        var key=new Pair(base,mask);
        var existing=composites.get(key);if(existing!=null)return existing;
        if(failures.containsKey(key))throw new IllegalArgumentException(failures.get(key));
        if(composites.size()>=256)throw new IllegalArgumentException("Masked entity texture cache exceeds 256 combinations");
        var minecraft=Minecraft.getInstance();
        NativeImage image=null;DynamicTexture texture=null;
        try(var baseStream=minecraft.getResourceManager().open(base);
            var maskStream=minecraft.getResourceManager().open(mask);
            var first=NativeImage.read(baseStream);var second=NativeImage.read(maskStream)) {
            int width=BedrockTextureComposition.commonSize(first.getWidth(),second.getWidth());
            int height=BedrockTextureComposition.commonSize(first.getHeight(),second.getHeight());
            long count=(long)width*height;
            if(pixels+count>16_777_216)throw new IllegalArgumentException("Masked entity texture cache exceeds pixel budget");
            image=new NativeImage(width,height,false);
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)
                image.setPixel(x,y,BedrockTextureComposition.masked(
                    first.getPixel(x*first.getWidth()/width,y*first.getHeight()/height),
                    second.getPixel(x*second.getWidth()/width,y*second.getHeight()/height)));
            var id=Identifier.fromNamespaceAndPath("bedrock_compat","native_composite/"+generation+"/"+composites.size());
            texture=new DynamicTexture(()->"Bedrock masked entity texture",image);image=null;
            minecraft.getTextureManager().register(id,texture);texture=null;
            composites.put(key,id);pixels+=count;
            return id;
        }catch(IOException|RuntimeException exception){
            String message="Cannot compose masked entity textures: "+base+", "+mask+": "+exception.getMessage();
            if(failures.size()<256)failures.put(key,message);
            throw new IllegalArgumentException(message,exception);
        }
        finally {if(texture!=null)texture.close();else if(image!=null)image.close();}
    }
}
