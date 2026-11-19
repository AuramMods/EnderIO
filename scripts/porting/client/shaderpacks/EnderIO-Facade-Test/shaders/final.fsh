#version 150 compatibility

uniform sampler2D colortex0;
in vec2 screenUv;
out vec4 finalColor;

void main() {
    finalColor = texture(colortex0, screenUv);
    // A visible signature outside the scene makes a silent shader fallback detectable.
    if (gl_FragCoord.x < 48.0 && gl_FragCoord.y < 48.0) {
        finalColor = vec4(1.0, 0.0, 1.0, 1.0);
    }
}
