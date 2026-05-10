#version 320 es
precision highp float;          // highp лучше для блюра и CA

in vec2 v_texCoord;

uniform sampler2D u_texture;
uniform float u_blurAmount;
uniform vec2 u_resolution;
//uniform float u_chromaticAberration;   // ← раскомментируй, если хочешь управлять из кода

out vec4 fragColor;

void main() {
    vec2 texelSize = 1.0 / u_resolution;
    float blurRadius = u_blurAmount;
    float caAmount = 4.5;                    // ← можно заменить на u_chromaticAberration

    // Radial offset для хроматической аберрации
    vec2 caDir = (v_texCoord - vec2(0.5)) * (caAmount * 2.0) * texelSize;

    vec2 uvRed   = v_texCoord + caDir;
    vec2 uvGreen = v_texCoord;
    vec2 uvBlue  = v_texCoord - caDir;

    // 9-tap Gaussian blur для каждого канала отдельно
    vec4 blurRed = vec4(0.0);
    blurRed += texture(u_texture, uvRed + vec2(-1.0, -1.0) * texelSize * blurRadius) * 0.0625;
    blurRed += texture(u_texture, uvRed + vec2( 0.0, -1.0) * texelSize * blurRadius) * 0.125;
    blurRed += texture(u_texture, uvRed + vec2( 1.0, -1.0) * texelSize * blurRadius) * 0.0625;

    blurRed += texture(u_texture, uvRed + vec2(-1.0,  0.0) * texelSize * blurRadius) * 0.125;
    blurRed += texture(u_texture, uvRed + vec2( 0.0,  0.0) * texelSize * blurRadius) * 0.25;
    blurRed += texture(u_texture, uvRed + vec2( 1.0,  0.0) * texelSize * blurRadius) * 0.125;

    blurRed += texture(u_texture, uvRed + vec2(-1.0,  1.0) * texelSize * blurRadius) * 0.0625;
    blurRed += texture(u_texture, uvRed + vec2( 0.0,  1.0) * texelSize * blurRadius) * 0.125;
    blurRed += texture(u_texture, uvRed + vec2( 1.0,  1.0) * texelSize * blurRadius) * 0.0625;

    vec4 blurGreen = vec4(0.0);
    blurGreen += texture(u_texture, uvGreen + vec2(-1.0, -1.0) * texelSize * blurRadius) * 0.0625;
    blurGreen += texture(u_texture, uvGreen + vec2( 0.0, -1.0) * texelSize * blurRadius) * 0.125;
    blurGreen += texture(u_texture, uvGreen + vec2( 1.0, -1.0) * texelSize * blurRadius) * 0.0625;

    blurGreen += texture(u_texture, uvGreen + vec2(-1.0,  0.0) * texelSize * blurRadius) * 0.125;
    blurGreen += texture(u_texture, uvGreen + vec2( 0.0,  0.0) * texelSize * blurRadius) * 0.25;
    blurGreen += texture(u_texture, uvGreen + vec2( 1.0,  0.0) * texelSize * blurRadius) * 0.125;

    blurGreen += texture(u_texture, uvGreen + vec2(-1.0,  1.0) * texelSize * blurRadius) * 0.0625;
    blurGreen += texture(u_texture, uvGreen + vec2( 0.0,  1.0) * texelSize * blurRadius) * 0.125;
    blurGreen += texture(u_texture, uvGreen + vec2( 1.0,  1.0) * texelSize * blurRadius) * 0.0625;

    vec4 blurBlue = vec4(0.0);
    blurBlue += texture(u_texture, uvBlue + vec2(-1.0, -1.0) * texelSize * blurRadius) * 0.0625;
    blurBlue += texture(u_texture, uvBlue + vec2( 0.0, -1.0) * texelSize * blurRadius) * 0.125;
    blurBlue += texture(u_texture, uvBlue + vec2( 1.0, -1.0) * texelSize * blurRadius) * 0.0625;

    blurBlue += texture(u_texture, uvBlue + vec2(-1.0,  0.0) * texelSize * blurRadius) * 0.125;
    blurBlue += texture(u_texture, uvBlue + vec2( 0.0,  0.0) * texelSize * blurRadius) * 0.25;
    blurBlue += texture(u_texture, uvBlue + vec2( 1.0,  0.0) * texelSize * blurRadius) * 0.125;

    blurBlue += texture(u_texture, uvBlue + vec2(-1.0,  1.0) * texelSize * blurRadius) * 0.0625;
    blurBlue += texture(u_texture, uvBlue + vec2( 0.0,  1.0) * texelSize * blurRadius) * 0.125;
    blurBlue += texture(u_texture, uvBlue + vec2( 1.0,  1.0) * texelSize * blurRadius) * 0.0625;

    // Финальный цвет
    vec4 color = vec4(blurRed.r, blurGreen.g, blurBlue.b, blurGreen.a);

    fragColor = color;
}
