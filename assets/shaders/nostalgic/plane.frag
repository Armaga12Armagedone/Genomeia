#version 300 es
precision mediump float;

// Постоянный цвет плоскости. В проходе затемнения он идёт множителем к кадру
// (glBlendFunc(GL_ZERO, GL_SRC_COLOR)), в проходе подсчёта запись цвета выключена.

uniform vec4 u_color;

out vec4 fragColor;

void main() {
    fragColor = u_color;
}
