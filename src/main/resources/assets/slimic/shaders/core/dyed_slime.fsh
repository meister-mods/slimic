#version 150
#moj_import <fog.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (texel.a < 0.1) discard;
    float brightness = max(texel.r, max(texel.g, texel.b));
    vec4 color = vec4(vec3(brightness), texel.a) * vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
