package dev.matanyah.bedrockcompat.client.entity;

import com.mojang.blaze3d.platform.NativeImage;
import dev.matanyah.bedrockcompat.bedrock.entity.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Resource-generation-bound, immutable voxel meshes; source images are always closed. */
final class NativeTextureMeshes {
    record Layer(BedrockGeometry geometry,String texture) {}
    private record Key(BedrockGeometry geometry,String bone,BedrockGeometry.TextureMesh mesh,String texture) {}
    private static NativeEntityResources.Snapshot generation;
    private static final Map<Key,Layer> cache=new LinkedHashMap<>();
    private static int polygons;
    static List<Layer> layers(BedrockGeometry geometry,Map<String,Object> aliases,NativeEntityResources.Snapshot resources) {
        if(generation!=resources){cache.clear();polygons=0;generation=resources;}
        List<Layer> layers=new ArrayList<>();
        for(var bone:geometry.bones())for(var mesh:bone.textureMeshes()) {
            Object alias=aliases.get("texture."+mesh.texture().toLowerCase(Locale.ROOT));
            if(!(alias instanceof String texture))throw new IllegalArgumentException("Missing texture-mesh alias: "+mesh.texture());
            var key=new Key(geometry,bone.name(),mesh,texture);var layer=cache.get(key);
            if(layer==null) {
                if(cache.size()>=256)throw new IllegalArgumentException("Texture mesh cache exceeds entry budget");
                var id=Identifier.fromNamespaceAndPath("bedrock_native",texture.endsWith(".png")?texture:texture+".png");
                try(var stream=Minecraft.getInstance().getResourceManager().open(id);var image=NativeImage.read(stream)) {
                    var faces=BedrockTextureMesh.polygons(mesh,image.getWidth(),image.getHeight(),geometry.textureWidth(),geometry.textureHeight(),bone.pivot().y(),image::getPixel);
                    if(polygons+faces.size()>1_048_576)throw new IllegalArgumentException("Texture mesh cache exceeds polygon budget");
                    layer=new Layer(BedrockTextureMesh.layer(geometry,bone.name(),faces),texture);polygons+=faces.size();cache.put(key,layer);
                }catch(java.io.IOException error){throw new IllegalArgumentException("Cannot load texture-mesh image: "+id,error);}
            }
            layers.add(layer);
        }
        return List.copyOf(layers);
    }
}
