out vec2 surfaceUv;
out vec2 lightUv;
out vec4 surfaceTint;

void main() {
    gl_Position = ftransform();
    surfaceUv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lightUv = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    surfaceTint = gl_Color;
}
