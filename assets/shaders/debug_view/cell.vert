#version 300 es
precision highp float;

// Клетка отладочного режима. Общий вершинный шейдер для обоих проходов по клеткам
// DebugRenderer: заливки (cell_fill.frag) и сглаживания границ (cell_edge.frag).
//
// Геометрия — описанный восьмиугольник из CellGeometry (Detail.HULL), раздутый на
// AA_MARGIN_PX пикселей: второму проходу нужны фрагменты чуть ЗА окружностью, чтобы
// размыть край клетки наружу. Точное d/R считает фрагментный шейдер по v_local — позиция
// интерполируется растеризатором точно, в отличие от расстояния.
layout(location = 0) in vec3 a_corner;  // xy — точка корпуса в долях радиуса

// Инстансные атрибуты, раскладка — в CellInstanceBuffer.
layout(location = 1) in vec2 a_center;
layout(location = 2) in vec4 a_color;   // r, g, b, a
layout(location = 3) in vec4 a_shape;   // cos, sin, радиус, энергия
layout(location = 4) in vec4 a_type;    // тип клетки, ключ шума lo, hi, флаги

uniform mat4 u_projTrans;

/** Сколько единиц мира приходится на один пиксель экрана. */
uniform float u_pixelWorld;

/**
 * Радиус ядра в долях радиуса клетки при энергии ENERGY_SPAN. Ядро — индикатор энергии,
 * растёт с ней линейно, как в ностальгическом режиме.
 */
uniform float u_nucleusAtFullEnergy;

/** На сколько пикселей корпус выходит за окружность: полоса сглаживания края. */
const float AA_MARGIN_PX = 1.5;

/** Точка в долях радиуса клетки: длина 1 — окружность. */
out vec2 v_local;

flat out vec3 v_color;

/** Шаг d/R на один пиксель экрана: перевод «долей радиуса» в пиксели. */
flat out float v_tPerPixel;

/** Радиус ядра в долях R; не больше нуля — ядра нет. */
flat out float v_nucleus;

/** Направление клетки (cos, sin) — по нему крутится узор. */
flat out vec2 v_dir;

/** Нарисовать на клетке спираль — см. CellInstanceBuffer.FLAG_SPIRAL. */
flat out float v_spiral;

void main() {
    // Распаковка — та же, что в circle_pc.vert: RADIUS_MIN + байт * RADIUS_SPAN.
    float radius = 0.05 + a_shape.z * 0.7;

    v_tPerPixel = u_pixelWorld / radius;

    float grow = 1.0 + AA_MARGIN_PX * v_tPerPixel;
    v_local = a_corner.xy * grow;

    v_color = a_color.rgb;
    v_dir = vec2(a_shape.x * 2.0 - 1.0, a_shape.y * 2.0 - 1.0);

    // Байт флагов обратно в целое — с округлением, как byteValue() в circle_pc.vert.
    float flags = floor(a_type.w * 255.0 + 0.5);
    bool isCell = mod(flags, 2.0) < 1.0;                  // FLAG_NOT_CELL = 1
    v_spiral = mod(floor(flags / 2.0), 2.0);              // FLAG_SPIRAL = 2

    v_nucleus = isCell ? a_shape.w * u_nucleusAtFullEnergy : -1.0;

    gl_Position = u_projTrans * vec4(v_local * radius + a_center, 0.0, 1.0);
}
