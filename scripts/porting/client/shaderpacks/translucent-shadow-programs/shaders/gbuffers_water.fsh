#version 150 compatibility

#include "/qa/stained_glass_marker.glsl"

uniform sampler2D gtexture;
uniform sampler2D lightmap;
uniform float viewHeight;
in vec2 surfaceUv;
in vec2 lightUv;
in vec4 surfaceTint;
out vec4 terrainColor;

/* DRAWBUFFERS:0 */

void main() {
    vec4 surface = texture(gtexture, surfaceUv);
    float alpha = surface.a * surfaceTint.a;
    if (alpha < 0.1) discard;

    vec3 marker = stainedGlassMarker(surface);
    float screenY = gl_FragCoord.y / viewHeight;
    if (screenY > 0.44 && screenY < 0.47 && marker != vec3(0.0)) {
        terrainColor = vec4(marker, 1.0);
    } else {
        terrainColor = vec4(surface.rgb * surfaceTint.rgb * texture(lightmap, lightUv).rgb, alpha);
    }
}
