// Общая часть фрагментных шейдеров клеток отладочного режима: cell_fill.frag и
// cell_edge.frag. Вставляется на место строки #include, см. DebugRenderer.compile.
//
// Оба прохода обязаны красить клетку ОДИНАКОВО: заливка рисует внутренность, проход
// сглаживания — полупиксельную полосу по её краю. Разойдись они в формулах цвета, на
// границе клеток появилась бы ступенька.

/** Ширина мембраны — светлой полосы по краю тела, в пикселях. */
const float MEMBRANE_PX = 1.25;

/** Полуширина стенки между соседними клетками, в пикселях. */
const float WALL_HALF_PX = 0.6;

/** Непрозрачность стенки между клетками: стенка — затемнение, а не свой цвет. */
const float WALL_ALPHA = 0.45;

/**
 * Спираль хамелеона, всё в долях радиуса клетки: шаг витков, где она кончается, и
 * полуширина линии — у центра и на конце хвоста.
 */
const float SPIRAL_STEP = 0.32;
const float SPIRAL_END = 0.74;
const float SPIRAL_HALF_WIDTH_CENTER = 0.075;
const float SPIRAL_HALF_WIDTH_END = 0.025;

/**
 * Покрытие пикселя фигурой, если до её края [px] пикселей (плюс — внутрь).
 * Ящичный фильтр шириной в пиксель: ровно то, что дал бы MSAA с бесконечным числом
 * выборок, но за одно вычитание.
 */
float coverage(float px) {
    return clamp(0.5 + px, 0.0, 1.0);
}

/**
 * Градиент d/R клетки в пикселе, в долях радиуса на пиксель: направление от её центра
 * к пикселю, длина — tPerPixel. По разности градиентов двух клеток проход сглаживания
 * переводит разность их d/R в пиксели до границы между ними.
 */
vec2 tGradient(vec2 local, float t, float tPerPixel) {
    vec2 direction = t > 1e-6 ? local / t : vec2(1.0, 0.0);
    return direction * tPerPixel;
}

/**
 * Упаковка градиента владельца в RGBA8: rg — направление, b — tPerPixel в логарифме
 * (2^-14..1, шаг ~4 %: одинаково точно и для клетки в пару пикселей, и во весь экран),
 * a = 1 — пиксель занят клеткой (у пустоты после очистки ноль).
 */
vec4 encodeOwner(vec2 local, float t, float tPerPixel) {
    vec2 direction = t > 1e-6 ? local / t : vec2(1.0, 0.0);
    float scale = clamp((log2(tPerPixel) + 14.0) / 14.0, 0.0, 1.0);
    return vec4(direction * 0.5 + 0.5, scale, 1.0);
}

vec2 decodeOwnerGradient(vec4 owner) {
    vec2 direction = owner.rg * 2.0 - 1.0;
    float len = length(direction);
    direction = len > 1e-3 ? direction / len : vec2(1.0, 0.0);
    return direction * exp2(owner.b * 14.0 - 14.0);
}

float luminance(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

/**
 * Мелкие детали (мембрана, стенки, узор) гаснут, когда клетка становится меньше
 * нескольких пикселей: иначе на отдалении клетка из одних полос превращается в рябь.
 */
float detailFade(float tPerPixel) {
    return smoothstep(3.0, 8.0, 1.0 / tPerPixel);
}

/**
 * Цвет заливки на расстоянии t (доли радиуса) от центра.
 * Лёгкий объём: к ободу чуть темнее. Чистый цвет из defaultColor — примерно на трети радиуса.
 */
vec3 cellShade(vec3 base, float t) {
    return base * mix(1.05, 0.86, t * t);
}

/** Мембрана: светлее заливки, на тёмном фоне читается как оболочка тела. */
vec3 membraneColor(vec3 base) {
    return mix(base, vec3(1.0), 0.55);
}

/** Цвет самого обода — с учётом того, что у мелкой клетки мембраны уже нет. */
vec3 rimColor(vec3 base, float fade) {
    return mix(cellShade(base, 1.0), membraneColor(base), fade);
}

/**
 * Спираль — отличительный знак хамелеона: его цвет меняется сигналом, и по одному цвету
 * клетку не опознать. Архимедова спираль, витки на равном расстоянии SPIRAL_STEP,
 * поворачивается вместе с клеткой. Тушь — контрастная к текущему цвету: тёмная на
 * светлом, светлая на тёмном.
 */
vec3 drawSpiral(vec3 color, vec3 base, vec2 local, vec2 dir, float tPerPixel, float fade) {
    vec2 p = vec2(dot(local, dir), dot(local, vec2(-dir.y, dir.x)));
    float r = length(p);
    float turn = atan(p.y, p.x) / 6.2831853;

    // Виток там, где r = SPIRAL_STEP * (turn + k): расстояние до ближайшего — по радиусу.
    float phase = r / SPIRAL_STEP - turn;
    float d = abs(fract(phase + 0.5) - 0.5) * SPIRAL_STEP;

    // Линия сужается от центра к концу — получается закрученный хвост, а не мишень.
    float halfWidth = mix(SPIRAL_HALF_WIDTH_CENTER, SPIRAL_HALF_WIDTH_END, clamp(r / SPIRAL_END, 0.0, 1.0));
    float stroke = coverage((halfWidth - d) / tPerPixel);
    // Хвост обрывается, не доходя до мембраны: кончик — скруглённый край по радиусу.
    stroke *= coverage((SPIRAL_END - r) / tPerPixel);
    stroke *= fade;

    vec3 ink = luminance(base) > 0.5 ? base * 0.22 : mix(base, vec3(1.0), 0.78);
    return mix(color, ink, stroke);
}
