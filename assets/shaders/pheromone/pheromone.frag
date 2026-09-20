#version 300 es
precision highp float;

in vec2 v_localUV;
in float v_A;
in float v_radius;
flat in vec3 ex_Color;

uniform float u_K;

out vec4 fragColor;

void main() {
    float distSq = dot(v_localUV, v_localUV);

    if (distSq > 1.0) discard;

    float alpha = v_A / (1.0 + u_K * distSq * v_radius);

    fragColor = vec4(ex_Color, alpha * 0.7);
}
