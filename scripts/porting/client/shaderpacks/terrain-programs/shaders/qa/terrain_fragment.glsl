uniform sampler2D gtexture;
uniform sampler2D lightmap;
uniform float viewWidth;
uniform float viewHeight;
in vec2 surfaceUv;
in vec2 lightUv;
in vec4 surfaceTint;
out vec4 terrainColor;

void main() {
    vec4 surface = texture(gtexture, surfaceUv);
    float alpha = surface.a * surfaceTint.a;
    if (alpha < 0.1) {
        discard;
    }
    vec3 lit = surface.rgb * surfaceTint.rgb * texture(lightmap, lightUv).rgb;
    vec2 screen = gl_FragCoord.xy / vec2(viewWidth, viewHeight);
    // Tag a stone patch and a corner of the first facade in separate terrain programs.
#ifdef QA_CUTOUT
    if (screen.x > 0.365 && screen.x < 0.375 && screen.y > 0.48 && screen.y < 0.52) {
        lit = vec3(1.0, 1.0, 0.0);
    }
#else
    if (screen.x > 0.22 && screen.x < 0.28 && screen.y > 0.22 && screen.y < 0.28) {
        lit = vec3(0.0, 1.0, 1.0);
    }
#endif
    terrainColor = vec4(lit, alpha);
}
