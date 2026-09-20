#version 320 es
precision highp float;

layout(location = 0) in vec2 a_position;   // quad (-1..1)
layout(location = 1) in vec2 a_pos;        // instance world position
layout(location = 2) in float a_A;         // intensity
layout(location = 3) in uint a_color;      // packed color (точно как putInt)

uniform mat4 u_projTrans;
uniform float u_K;
uniform float u_P;

out vec2 v_localUV;
out float v_A;
out float v_radius;
flat out vec3 ex_Color;

void main() {
    v_A = a_A;

    float squaredRadius = max((a_A / u_P - 1.0) / u_K, 0.0);
    float radius = sqrt(squaredRadius);

    ex_Color = unpackUnorm4x8(a_color).rgb;

    vec2 offset = a_position * radius;

    gl_Position = u_projTrans * vec4(a_pos + offset, 0.0, 1.0);

    v_localUV = a_position;
    v_radius  = squaredRadius;
}
