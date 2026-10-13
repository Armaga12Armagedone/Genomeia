#version 300 es
precision mediump float;

// Проход 2 отладочного режима: заливка клеток из FBO — на экран, поверх фона.
//
// Цвет в FBO премножен на покрытие, поэтому сглаженный край тела ложится на фон без
// ореола. Фон рисуется здесь же, а не очисткой экрана: он часть картинки режима.
// texelFetch, а не texture: FBO ровно в размер экрана, и фильтрация тут не нужна.

uniform sampler2D u_texture;
uniform vec3 u_background;

out vec4 fragColor;

void main() {
    vec4 cells = texelFetch(u_texture, ivec2(gl_FragCoord.xy), 0);
    fragColor = vec4(u_background * (1.0 - cells.a) + cells.rgb, 1.0);
}
