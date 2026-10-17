#version 300 es
precision mediump float;

// Вывод готового кадра ностальгического режима из scene FBO на экран, один в один.
//
// Шейдером, а не glBlitFramebuffer: блит в GLES 3.0 запрещён, если у экранного буфера
// есть мультисэмплинг, а экранный буфер у каждой платформы свой. Полноэкранный квад с
// одной выборкой работает везде, где работает виньетка, и стоит столько же.

in vec2 v_texCoord;
uniform sampler2D u_texture;

out vec4 fragColor;

void main() {
    fragColor = texture(u_texture, v_texCoord);
}
