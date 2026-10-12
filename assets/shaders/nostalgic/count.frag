#version 300 es
precision highp float;

// Проход подсчёта контура ностальгического режима (см. NostalgicRenderer).
//
// Запись цвета и глубины в этом проходе выключена: фрагмент нужен только для того, чтобы
// пройти или не пройти тест глубины, а stencil увеличивает сам тест. Глубина — та же
// точная d/R, что и в nostalgic.frag, только без подъёма: иначе подсчёт и заливка
// сравнивали бы разные поверхности, и на краях контура вернулась бы волна.

in vec2 ex_Local;

uniform float u_depthScale;

void main() {
    float d = length(ex_Local);
    if (d > 1.0) discard;
    gl_FragDepth = d * u_depthScale;
}
