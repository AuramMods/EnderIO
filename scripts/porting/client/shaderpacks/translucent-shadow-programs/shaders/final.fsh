#version 150 compatibility

uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
uniform float viewWidth;
uniform float viewHeight;
in vec2 screenUv;
out vec4 finalColor;

void main() {
    finalColor = texture(colortex0, screenUv);
    vec2 shadowUv = (gl_FragCoord.xy - vec2(viewWidth - 256.0, viewHeight - 256.0)) / 256.0;
    if (shadowUv.x >= 0.0 && shadowUv.y >= 0.0) {
        vec3 shadow = texture(shadowcolor0, shadowUv).rgb;
        finalColor = vec4(shadow, 1.0);
    }
    if (gl_FragCoord.x < 48.0 && gl_FragCoord.y < 48.0) {
        finalColor = vec4(1.0, 0.0, 1.0, 1.0);
    }
}
