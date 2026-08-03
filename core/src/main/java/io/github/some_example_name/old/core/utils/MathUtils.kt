package io.github.some_example_name.old.core.utils

import kotlin.math.*

fun distanceTo(px: Float, py: Float, x: Float, y: Float): Float {
    val dx = px - x
    val dy = py - y
    val sqrt = dx * dx + dy * dy
    if (sqrt <= 0) return 0f
    val result = sqrt(sqrt)
    if (result.isNaN()) return 0f//throw Exception("TODO потом убрать")
    return result
}

/**
 * Быстрый обратный корень (трюк Quake III), одна итерация Ньютона.
 * Относительная погрешность ~0.17%.
 *
 * floatToRawIntBits вместо floatToIntBits: второй по спецификации обязан приводить
 * все NaN к каноническому 0x7fc00000, то есть тащит за собой проверку на NaN.
 * raw-версия — чистое перекладывание битов (movd), без ветки.
 *
 * ВАЖНО: результат нужно использовать как множитель. Писать 1f / invSqrt(x) бессмысленно —
 * это и деление, и погрешность аппроксимации; для такого случая есть обычный sqrt(x).
 */
fun invSqrt(x: Float): Float {
    val xhalf = 0.5f * x
    var i = java.lang.Float.floatToRawIntBits(x)
    i = 0x5f3759df - (i shr 1)
    var y = java.lang.Float.intBitsToFloat(i)
    y *= (1.5f - xhalf * y * y)
    return y
}

/**
 * То же, но с двумя итерациями Ньютона: погрешность падает с ~0.17% до ~0.0005%,
 * что уже точнее, чем нужно почти всегда. Цена второй итерации — 3 операции
 * (два умножения и вычитание, ~5 циклов), при этом всё ещё дешевле пары sqrt + деление.
 *
 * Использовать там, где погрешность первой итерации заметна: длинные цепочки
 * нормализаций, накопление силы за много тиков.
 */
fun invSqrtPrecise(x: Float): Float {
    val xhalf = 0.5f * x
    var i = java.lang.Float.floatToRawIntBits(x)
    i = 0x5f3759df - (i shr 1)
    var y = java.lang.Float.intBitsToFloat(i)
    y *= (1.5f - xhalf * y * y)
    y *= (1.5f - xhalf * y * y)
    return y
}
