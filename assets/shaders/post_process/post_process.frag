#version 320 es
precision highp float;

in vec2 v_texCoord;
uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform float u_zoom;

out vec4 fragColor;

void main() {

    vec4 textureColor = texture(u_texture, v_texCoord) * 1.4;

    // размер одного пикселя
    vec2 texel = 1.0 / u_resolution;

    // 4 сэмпла (Sobel)
    float p00 = dot(texture(u_texture, v_texCoord - texel).rgb, vec3(0.299, 0.587, 0.114));
    float p11 = dot(texture(u_texture, v_texCoord + texel).rgb, vec3(0.299, 0.587, 0.114));
    float p10 = dot(texture(u_texture, v_texCoord + vec2(texel.x, -texel.y)).rgb, vec3(0.299, 0.587, 0.114));
    float p01 = dot(texture(u_texture, v_texCoord + vec2(-texel.x, texel.y)).rgb, vec3(0.299, 0.587, 0.114));

    float gx = p00 - p11;
    float gy = p10 - p01;

    float edge = length(vec2(gx, gy));
    edge = smoothstep(0.0, u_zoom, edge);

    vec4 background = vec4(1.0, 0.969, 0.855, 1.0);

    vec4 textureMixBackground = mix(background, textureColor, 0.1875);

    float gray = (textureColor.r + textureColor.g + textureColor.b) / 3.0;
    gray = step(0.06, gray);

    vec4 result = mix(background, textureMixBackground, gray);

    float white = 1.0 - edge;

    //Lerp white
    float p1 = 0.0;
    float p2 = 0.98;
    vec4 color1 = vec4(0.678, 0.569, 0.435, 1.0);
    vec4 color2 = vec4(1.0);
    vec4 color = mix(color1, color2, (white - p1) / (p2 - p1));

    vec4 colorA = color * result;
    fragColor = colorA;
}