package dev.matanyah.bedrockcompat.client.particle;

import com.mojang.blaze3d.pipeline.*;
import net.minecraft.client.particle.SingleQuadParticle.Layer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;

final class ParticleLayers {
    static final Layer ADDITIVE=new Layer(true,TextureAtlas.LOCATION_PARTICLES,additive());
    private static RenderPipeline additive() {
        var source=RenderPipelines.TRANSLUCENT_PARTICLE;
        var builder=RenderPipeline.builder().withLocation(net.minecraft.resources.Identifier.parse("bedrock_compat:particle_additive"))
            .withVertexShader(source.getVertexShader()).withFragmentShader(source.getFragmentShader())
            .withCull(source.isCull()).withPolygonMode(source.getPolygonMode()).withPrimitiveTopology(source.getPrimitiveTopology())
            .withDepthStencilState(source.getDepthStencilState()).withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE));
        source.getShaderDefines().values().forEach((name,value)->builder.withShaderDefine(name+" "+value));
        source.getShaderDefines().flags().forEach(builder::withShaderDefine);
        source.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
        for(int i=0;i<source.getVertexFormatBindings().length;i++)builder.withVertexBinding(i,source.getVertexFormatBinding(i));
        return builder.build();
    }
}
