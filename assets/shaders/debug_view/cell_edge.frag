#version 300 es
precision highp float;

// Проход 3 отладочного режима: сглаживание краёв клеток и стенки между ними — прямо в
// экран, поверх готовой заливки.
//
// КАК ЭТО РАБОТАЕТ
// ----------------
// Заливка (проход 1) оставила в глубине минимальное d/R по пикселю — то есть d/R
// владельца, а во втором буфере — его градиент d/R. Здесь каждая клетка снова проходит
// по своему корпусу и сравнивает своё d/R с этим минимумом:
//   - равны — пиксель её собственный, заливка его уже нарисовала: discard;
//   - больше — пиксель чужой, и разница d/R, делённая на разность градиентов, — это
//     расстояние в пикселях до границы её владений.
// По этой дистанции проигравшая клетка подмешивает свой цвет в полупиксельную полосу у
// границы (это и есть сглаживание: с двух сторон по половине пикселя) и рисует свою
// половину стенки. Вторую половину рисует сосед — с его стороны проигравшая уже она.
//
// Внешний край тела устроен так же, только «соседом» выступает пустота с d/R = 1: глубина
// очищена именно этим значением. Получается аналитический аналог MSAA — без лишних
// выборок, по одному чтению глубины на фрагмент, и большинство фрагментов (далеко от
// границ) отбрасываются сразу после него.

#include "cell_common.glsl"

in vec2 v_local;
flat in vec3 v_color;
flat in float v_tPerPixel;
flat in float v_nucleus;
flat in vec2 v_dir;
flat in float v_spiral;

/** Глубина прохода заливки: d/R владельца пикселя, умноженное на u_depthScale. */
uniform sampler2D u_depth;
uniform float u_depthScale;

/** Градиент d/R владельца пикселя — см. encodeOwner в cell_common.glsl. */
uniform sampler2D u_owner;

out vec4 fragColor;

/**
 * Владелец пикселя — это я сам, если его градиент d/R совпадает с моим (с точностью до
 * упаковки в байты, это несколько процентов).
 *
 * Сравнивать одни только d/R нельзя: два прохода — две разные программы, и интерполятор
 * вправе разойтись в младших битах. На Intel UHD d/R одной и той же клетки в одном
 * пикселе расходился на ~1.5e-5, и клетка рисовала «стенку» сама себе — тёмный клин
 * внутри. А любой порог по d/R задевал и настоящих соседей у самой границы: стенка
 * шла пунктиром. Градиент же у соседа другой всегда — иначе у них не было бы границы.
 */
const float SELF_SLOPE = 0.15;

void main() {
    float t = length(v_local);
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float tMin = texelFetch(u_depth, pixel, 0).r / u_depthScale;
    float gap = t - tMin;

    // Точный владелец — сразу, без второй выборки: таких фрагментов большинство.
    if (gap <= 0.0) discard;

    float tpp = v_tPerPixel;

    // До собственного обода — точно. До стенки с владельцем — по разности градиентов
    // d/R двух клеток: она и есть скорость, с которой растёт gap на пиксель.
    float rimPx = (t - 1.0) / tpp;
    vec4 owner = texelFetch(u_owner, pixel, 0);
    float wallPx = 1e6;
    if (owner.a > 0.5) {
        vec2 slope = tGradient(v_local, t, tpp) - decodeOwnerGradient(owner);
        float speed = length(slope);
        if (speed < SELF_SLOPE * tpp) discard;
        wallPx = gap / speed;
    }

    // Над пустотой владельца нет (owner.a = 0), и ближайшая граница — свой обод.
    bool atRim = rimPx >= wallPx;
    float distPx = atRim ? rimPx : wallPx;

    if (distPx > WALL_HALF_PX + 1.0) discard;

    float fade = detailFade(tpp);

    float cover = clamp(0.5 - distPx, 0.0, 0.5);
    vec3 color = atRim ? rimColor(v_color, fade) : cellShade(v_color, t);
    vec4 result = vec4(color * cover, cover);

    if (!atRim) {
        float wall = clamp(WALL_HALF_PX + 0.5 - wallPx, 0.0, 1.0) * WALL_ALPHA * fade;
        // Стенка поверх подмешанного цвета; чёрная, поэтому в премноженном виде — ноль.
        result = result * (1.0 - wall) + vec4(0.0, 0.0, 0.0, wall);
    }

    fragColor = result;
}
