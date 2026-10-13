#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    vec4 texel = texture(Sampler0, texCoord0);
#ifdef BEDROCK_ALPHA_TEST
#ifdef BEDROCK_EMISSIVE
    if (dot(texel, vec4(1.0)) == 0.0) discard;
#else
    if (texel.a < 0.5) discard;
#endif
#endif
#ifdef PER_FACE_LIGHTING
    vec4 tint = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
#else
    vec4 tint = vertexColor;
#endif
    vec4 color = texel * tint * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#ifdef BEDROCK_EMISSIVE
    // Alpha is the amount of ordinary lighting, not a blanket full-bright flag.
    color.rgb *= mix(vec3(1.0), lightMapColor.rgb, texel.a);
#ifndef BEDROCK_BLEND
    color.a = tint.a * ColorModulator.a;
#endif
#else
    color *= lightMapColor;
#endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
