vec3 stainedGlassMarker(vec4 surface) {
    if (surface.a <= 0.1 || surface.a >= 0.9) return vec3(0.0);
    vec3 color = surface.rgb;
    if (color.r > max(color.g, color.b) + 0.19) return vec3(1.0, 0.0, 0.2);
    if (color.b > max(color.r, color.g) + 0.19) return vec3(0.0, 0.2, 1.0);
    if (color.g > max(color.r, color.b) + 0.19) return vec3(0.2, 1.0, 0.0);
    return vec3(0.0);
}
