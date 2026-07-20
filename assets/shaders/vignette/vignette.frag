#version 300 es
precision highp float;

in vec2 v_texCoord;
uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform float u_vignetteEnabled;

out vec4 fragColor;

void main() {
    vec4 color = texture(u_texture, v_texCoord);

    vec2 pos = v_texCoord * 2.0 - 1.0;
    float aspect = u_resolution.x / u_resolution.y;
    pos.x *= aspect;
    float dist = length(pos);
    float maxDist = length(vec2(aspect, 1.0));
    float normDist = dist / maxDist;
    float radius = 0.9;
    float softness = 0.8;
    float vignette = smoothstep(radius, radius - softness, normDist);

    float vignetteFactor = mix(1.0, vignette, u_vignetteEnabled);

    fragColor = color * vignetteFactor;
}