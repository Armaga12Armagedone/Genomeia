#version 300 es
precision highp float;
precision highp int;
precision highp usampler2D;

layout (location = 0) in vec2 a_position;

uniform mat4 u_projTrans;
uniform float u_K;
uniform float u_P;

// RGBA32UI data texture: one texel per pheromone instance.
// CPU writes: putFloat(x), putFloat(y), putFloat(A), putInt(color)
// → four uint bit-patterns per texel.
uniform usampler2D u_data;
uniform int u_texWidth;

out vec2 v_localUV;
out float v_A;
out float v_radius;
flat out vec3 ex_Color;

void main() {
    int id = gl_InstanceID;
    int texX = id - (id / u_texWidth) * u_texWidth; // id % u_texWidth without remainder op variance
    int texY = id / u_texWidth;

    // Exact integer fetch — NEAREST, no filtering
    uvec4 ph = texelFetch(u_data, ivec2(texX, texY), 0);

    // Recover floats from raw bits (same bits CPU putFloat wrote)
    vec2 worldPos = vec2(uintBitsToFloat(ph.x), uintBitsToFloat(ph.y));
    v_A = uintBitsToFloat(ph.z);

    // Manual RGBA8 unpack — unpackUnorm4x8 is GLSL ES 3.10+ only, not in 300 es
    uint c = ph.w;
    ex_Color = vec3(
        float(c & 0xFFu),
        float((c >> 8u) & 0xFFu),
        float((c >> 16u) & 0xFFu)
    ) * (1.0 / 255.0);

    float squaredRadius = max((v_A / u_P - 1.0) / u_K, 0.0);
    float radius = sqrt(squaredRadius);

    vec2 offset = a_position * radius;

    gl_Position = u_projTrans * vec4(worldPos + offset, 0.0, 1.0);

    v_localUV = a_position;
    v_radius  = squaredRadius;
}
