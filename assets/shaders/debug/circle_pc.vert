#version 300 es
precision highp float;
precision highp int;
precision highp usampler2D;

layout(location = 0) in vec2 a_position;

uniform mat4 u_projTrans;

// RGBA32UI data texture: 2 texels per particle
// texel0: x_bits, y_bits, color, packed1
// texel1: packed2, pad, pad, pad
uniform usampler2D u_data;
uniform int u_texWidth;

out vec2 ex_Quad;
flat out vec2 ex_Centroid;
flat out vec3 ex_Color;
flat out float ex_R;
flat out float ex_R_2;
out vec2 ex_UV;
flat out float ex_AngleCos;
flat out float ex_AngleSin;
flat out float ex_Energy;
flat out int ex_cellType;

float hash(float n) {
    return fract(sin(n) * 43758.5453123);
}

// Manual RGBA8 unpack — unpackUnorm4x8 is GLSL ES 3.10+ only
vec4 unpackRGBA8(uint c) {
    return vec4(
        float(c & 0xFFu),
        float((c >> 8u) & 0xFFu),
        float((c >> 16u) & 0xFFu),
        float((c >> 24u) & 0xFFu)
    ) * (1.0 / 255.0);
}

void main() {
    int id = gl_InstanceID;

    // 2 texels per particle, tightly packed row-major
    int base = id * 2;
    int texY0 = base / u_texWidth;
    int texX0 = base - texY0 * u_texWidth;
    // second texel is always base+1; width is even so it stays on the same row
    int texX1 = texX0 + 1;

    uvec4 t0 = texelFetch(u_data, ivec2(texX0, texY0), 0);
    uvec4 t1 = texelFetch(u_data, ivec2(texX1, texY0), 0);

    vec2 pos = vec2(uintBitsToFloat(t0.x), uintBitsToFloat(t0.y));
    uint color   = t0.z;
    uint packed1 = t0.w;
    uint packed2 = t1.x;

    vec4 v1 = unpackRGBA8(packed1);
    vec4 v2 = unpackRGBA8(packed2);

    float cosA = v1.x * 2.0 - 1.0;
    float sinA = v1.y * 2.0 - 1.0;

    ex_R = 0.05 + v1.w * 0.7;
    float energy   = v2.x * 0.5;
    int   cellType = int(round(v2.y * 255.0));

    vec2 worldPos = a_position * ex_R + pos;

    ex_Quad = worldPos;
    ex_Centroid = pos;
    ex_Color = unpackRGBA8(color).rgb;
    ex_R_2 = ex_R * ex_R;
    ex_Energy = energy * energy;
    ex_UV = a_position * 0.5 + 0.5;
    ex_cellType = cellType;

    float noiseAngle = (hash(float(id)) - 0.5) * 3.0;
    float ca = cos(noiseAngle);
    float sa = sin(noiseAngle);
    float mirroredCos = cosA;
    float mirroredSin = -sinA;
    float nx = mirroredCos * ca - mirroredSin * sa;
    float ny = mirroredSin * ca + mirroredCos * sa;

    ex_AngleCos = nx;
    ex_AngleSin = ny;

    gl_Position = u_projTrans * vec4(worldPos, 0.0, 1.0);
}
