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
    private record EmissiveKey(Identifier texture,boolean alphaTest) {}
    private static final Map<EmissiveKey,Identifier> emissives=new LinkedHashMap<>();
    private static final Map<EmissiveKey,String> emissiveFailures=new LinkedHashMap<>();
    private record DyeKey(Identifier texture,int dye,boolean alphaTest) {}
    private static final Map<DyeKey,Identifier> dyes=new LinkedHashMap<>();
    private static long pixels;
    private static long generation;
    private NativeEntityTextures() {}

    static void reset() {
        NativeMaterialRenderTypes.reset();
        var manager=Minecraft.getInstance().getTextureManager();
        composites.values().forEach(manager::release);
        emissives.values().forEach(manager::release);
        dyes.values().forEach(manager::release);dyes.clear();
        emissives.clear();emissiveFailures.clear();
        composites.clear();failures.clear();pixels=0;generation++;
    }

    static Identifier dyed(Identifier source,int dye,boolean alphaTest) {
        var key=new DyeKey(source,dye,alphaTest);
        var existing=dyes.get(key);if(existing!=null)return existing;
        NativeImage image=null;
        try(var stream=Minecraft.getInstance().getResourceManager().open(source)) {
            if(dyes.size()+emissives.size()+composites.size()>=256)throw new IllegalArgumentException("Native texture cache exceeds combinations budget");
            image=NativeImage.read(stream);
            long count=(long)image.getWidth()*image.getHeight();
            if(pixels+count>16_777_216)throw new IllegalArgumentException("Native texture cache exceeds pixel budget");
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
                image.setPixel(x,y,BedrockTextureComposition.colorMask(image.getPixel(x,y),dye,alphaTest));
            var id=Identifier.fromNamespaceAndPath("bedrock_compat","native_dye/"+generation+"/"+dyes.size());
            var texture=new DynamicTexture(()->"Bedrock dye mask",image);image=null;
            Minecraft.getInstance().getTextureManager().register(id,texture);dyes.put(key,id);pixels+=count;return id;
        } catch(IOException exception){throw new IllegalArgumentException("Cannot prepare dye mask: "+source,exception);}
        finally{if(image!=null)image.close();}
    }

    static Identifier emissive(Identifier source,boolean alphaTest) {
        var key=new EmissiveKey(source,alphaTest);
        if(emissives.containsKey(key))return emissives.get(key);
        if(emissiveFailures.containsKey(key))throw new IllegalArgumentException(emissiveFailures.get(key));
        NativeImage image=null;DynamicTexture texture=null;
        try(var stream=Minecraft.getInstance().getResourceManager().open(source)) {
            if(emissives.size()+composites.size()>=256)throw new IllegalArgumentException("Native texture cache exceeds 256 combinations");
            image=NativeImage.read(stream);
            long count=(long)image.getWidth()*image.getHeight();
            if(pixels+count>16_777_216)throw new IllegalArgumentException("Native texture cache exceeds pixel budget");
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
                image.setPixel(x,y,BedrockTextureComposition.emissiveOpacity(image.getPixel(x,y),alphaTest));
            var id=Identifier.fromNamespaceAndPath("bedrock_compat","native_emissive/"+generation+"/"+emissives.size());
            texture=new DynamicTexture(()->"Bedrock emissive opacity",image);image=null;
            Minecraft.getInstance().getTextureManager().register(id,texture);texture=null;
            emissives.put(key,id);pixels+=count;return id;
        } catch(IOException|RuntimeException exception) {
            String message="Cannot prepare emissive entity texture: "+source+": "+exception.getMessage();
            if(emissiveFailures.size()<256)emissiveFailures.put(key,message);
            throw new IllegalArgumentException(message,exception);
        } finally {if(texture!=null)texture.close();else if(image!=null)image.close();}
    }

    static Identifier masked(Identifier base,Identifier mask) {
        var key=new Pair(base,mask);
        var existing=composites.get(key);if(existing!=null)return existing;
        if(failures.containsKey(key))throw new IllegalArgumentException(failures.get(key));
        if(composites.size()+emissives.size()>=256)throw new IllegalArgumentException("Native texture cache exceeds 256 combinations");
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
