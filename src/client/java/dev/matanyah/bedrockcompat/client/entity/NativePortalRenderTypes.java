package dev.matanyah.bedrockcompat.client.entity;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Aperture-clipped portal surfaces composite over world geometry, not through world depth. */
public final class NativePortalRenderTypes {
    private record Key(RenderType base,Identifier texture) {}
    private static final Map<Key,RenderType> CACHE=new HashMap<>();
    private NativePortalRenderTypes() {}
    public static RenderType overlay(RenderType base,Identifier texture) {
        return CACHE.computeIfAbsent(new Key(base,texture),key->{
            var source=base.pipeline();
            var builder=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("bedrock_compat",
                "portal/"+source.getLocation().getNamespace()+"/"+source.getLocation().getPath()))
                .withVertexShader(source.getVertexShader()).withFragmentShader(source.getFragmentShader())
                .withCull(source.isCull()).withPolygonMode(source.getPolygonMode())
                .withPrimitiveTopology(source.getPrimitiveTopology())
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS,false));
            source.getShaderDefines().values().forEach((name,value)->builder.withShaderDefine(name+" "+value));
            source.getShaderDefines().flags().forEach(builder::withShaderDefine);
            source.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
            for(int i=0;i<source.getVertexFormatBindings().length;i++)builder.withVertexBinding(i,source.getVertexFormatBinding(i));
            for(int i=0;i<source.getColorTargetStates().length;i++)builder.withColorTargetState(i,source.getColorTargetStates()[i]);
            return RenderType.create("crossrock_portal",RenderSetup.builder(builder.build())
                .withTexture("Sampler0",texture).useLightmap().useOverlay().createRenderSetup());
        });
    }
}
