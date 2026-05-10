#version 320 es
precision highp float;

layout(location = 0) in vec2 a_position;   // quad
layout(location = 1) in vec2 a_pos;        // instance position
layout(location = 2) in uint a_color;
layout(location = 3) in uint a_packed1;
layout(location = 4) in uint a_packed2;

uniform mat4 u_projTrans;

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

void main() {
    vec4 v1 = unpackUnorm4x8(a_packed1);
    vec4 v2 = unpackUnorm4x8(a_packed2);

    float cosA = v1.x * 2.0 - 1.0;
    float sinA = v1.y * 2.0 - 1.0;

    ex_R = 0.1 + v1.w * 0.4;
    int cellType = int(round(v2.y * 255.0));

    vec2 worldPos = a_position * ex_R + a_pos;

    ex_Quad = worldPos;
    ex_Centroid = a_pos;
    ex_Color = unpackUnorm4x8(a_color).rgb;
    ex_R_2 = ex_R * ex_R;
    ex_Energy = v2.x * 10.0 * 0.0;
    ex_UV = a_position * 0.5 + 0.5;
    ex_cellType = cellType;

    float noiseAngle = (hash(float(gl_InstanceID)) - 0.5) * 3.0;
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
