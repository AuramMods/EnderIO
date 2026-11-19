#version 150 compatibility

out vec2 screenUv;

void main() {
    screenUv = gl_MultiTexCoord0.xy;
    gl_Position = ftransform();
}
