package dev.matanyah.bedrockcompat.client.entity;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.matanyah.bedrockcompat.bedrock.entity.BedrockMaterials;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Preserve material state on translucent draws too, rather than using Java's eyes shader. */
final class NativeMaterialRenderTypes {
    private record Key(Identifier texture,BedrockMaterials.Material material,boolean translucent) {}
    private static final Map<Key,RenderType> CACHE=new HashMap<>();
    private static long serial;
    static void reset(){CACHE.clear();}
    static RenderType get(Identifier texture,BedrockMaterials.Material material,boolean translucent) {
        return CACHE.computeIfAbsent(new Key(texture,material,translucent),key->{
            var source=(material.blend()||translucent?RenderTypes.entityTranslucent(texture):RenderTypes.entityCutout(texture)).pipeline();
            var builder=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("bedrock_compat","material/"+serial++))
                .withVertexShader(source.getVertexShader()).withFragmentShader(Identifier.fromNamespaceAndPath("bedrock_compat","core/entity"))
                .withCull(material.cull()).withPolygonMode(source.getPolygonMode()).withPrimitiveTopology(source.getPrimitiveTopology());
            var depth=source.getDepthStencilState();
            builder.withDepthStencilState(new DepthStencilState(material.depthEqual()?com.mojang.blaze3d.platform.CompareOp.EQUAL:depth.depthTest(),material.depthWrite()&&depth.writeDepth()));
            // Keep vertex/fragment interfaces identical, but alpha is interpreted by Bedrock's shader rules.
            source.getShaderDefines().values().forEach((name,value)->{if(!name.equals("ALPHA_CUTOUT"))builder.withShaderDefine(name+" "+value);});
            source.getShaderDefines().flags().forEach(builder::withShaderDefine);
            if(material.alphaTest())builder.withShaderDefine("BEDROCK_ALPHA_TEST");
            if(material.emissive())builder.withShaderDefine("BEDROCK_EMISSIVE");
            if(material.blend())builder.withShaderDefine("BEDROCK_BLEND");
            source.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
            for(int i=0;i<source.getVertexFormatBindings().length;i++)builder.withVertexBinding(i,source.getVertexFormatBinding(i));
            for(int i=0;i<source.getColorTargetStates().length;i++) {
                var target=source.getColorTargetStates()[i];
                var blend=material.additive()?Optional.of(new BlendFunction(material.sourceAlpha()?BlendFactor.SRC_ALPHA:BlendFactor.ONE,BlendFactor.ONE)):target.blendFunction();
                builder.withColorTargetState(i,new ColorTargetState(blend,target.format(),material.colorWrite()?target.writeMask():ColorTargetState.WRITE_NONE));
            }
            var setup=RenderSetup.builder(builder.build()).withTexture("Sampler0",texture,()->{
                var filter=material.bilinear()?FilterMode.LINEAR:FilterMode.NEAREST;
                return material.repeat()?RenderSystem.getSamplerCache().getRepeat(filter):RenderSystem.getSamplerCache().getClampToEdge(filter);
            }).useLightmap().useOverlay();
            if(material.blend()||translucent)setup.sortOnUpload();
            return RenderType.create("crossrock_material",setup.createRenderSetup());
        });
    }
}
