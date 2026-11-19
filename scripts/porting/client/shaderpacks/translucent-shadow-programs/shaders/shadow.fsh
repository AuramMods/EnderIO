#version 150 compatibility

#include "/qa/stained_glass_marker.glsl"

const int shadowMapResolution = 512;
const float shadowDistance = 16.0;
const float shadowDistanceRenderMul = 1.0;

uniform sampler2D gtexture;
in vec2 surfaceUv;
out vec4 shadowColor;

/* DRAWBUFFERS:0 */

void main() {
    vec4 surface = texture(gtexture, surfaceUv);
    if (surface.a < 0.1) discard;
    vec3 marker = stainedGlassMarker(surface);
    shadowColor = vec4(marker == vec3(0.0) ? vec3(0.3) : marker, 1.0);
}
