#version 320 es
precision highp float;

in vec2 v_texCoord;

uniform sampler2D u_texture;
uniform float u_blurAmount;
uniform vec2 u_resolution;

out vec4 fragColor;

void main() {
    vec2 texelSize = 1.0 / u_resolution;
    float blurRadius = u_blurAmount;

    vec4 color = vec4(0.0);

    // 9-tap Gaussian
    color += texture(u_texture, v_texCoord + vec2(-1.0, -1.0) * texelSize * blurRadius) * 0.0625;
    color += texture(u_texture, v_texCoord + vec2( 0.0, -1.0) * texelSize * blurRadius) * 0.125;
    color += texture(u_texture, v_texCoord + vec2( 1.0, -1.0) * texelSize * blurRadius) * 0.0625;

    color += texture(u_texture, v_texCoord + vec2(-1.0,  0.0) * texelSize * blurRadius) * 0.125;
    color += texture(u_texture, v_texCoord + vec2( 0.0,  0.0) * texelSize * blurRadius) * 0.25;
    color += texture(u_texture, v_texCoord + vec2( 1.0,  0.0) * texelSize * blurRadius) * 0.125;

    color += texture(u_texture, v_texCoord + vec2(-1.0,  1.0) * texelSize * blurRadius) * 0.0625;
    color += texture(u_texture, v_texCoord + vec2( 0.0,  1.0) * texelSize * blurRadius) * 0.125;
    color += texture(u_texture, v_texCoord + vec2( 1.0,  1.0) * texelSize * blurRadius) * 0.0625;

    fragColor = color;
}
