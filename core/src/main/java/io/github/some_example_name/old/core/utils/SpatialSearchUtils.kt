package io.github.some_example_name.old.core.utils

import io.github.some_example_name.old.systems.physics.GridManager
import it.unimi.dsi.fastutil.ints.IntArrayList

/**
 * АЛЛОЦИРУЕТ результат. Использовать только там, где результат нужен как массив
 * (редкие пользовательские команды, морфогенез). Для обхода есть forEachParticle.
 */
fun GridManager.collectParticles(gridX: Int, gridY: Int, radius: Int = 3): IntArray {
    val list = IntArrayList((2 * radius + 1) * (2 * radius + 1) * maxAmountOfParticles)
    forEachParticle(gridX, gridY, radius) { list.add(it) }
    return list.toIntArray()
}

/**
 * Обход квадрата (2*radius+1)^2 клеток вокруг (gridX, gridY) без аллокаций.
 * Каждый ряд читается одним отрезком: индексы клеток внутри ряда последовательные.
 */
inline fun GridManager.forEachParticle(
    gridX: Int,
    gridY: Int,
    radius: Int,
    action: (Int) -> Unit
) {
    for (dy in -radius..radius) {
        forEachParticleInRowSegment(gridY + dy, gridX - radius, gridX + radius, action)
    }
}

/**
 * Обход только "кольца" на расстоянии radius (границы квадрата), без аллокаций.
 */
inline fun GridManager.forEachParticleOnRadius(
    gridX: Int,
    gridY: Int,
    radius: Int,
    action: (Int) -> Unit
) {
    // верхний и нижний ряды — целиком, вместе с углами
    forEachParticleInRowSegment(gridY - radius, gridX - radius, gridX + radius, action)
    if (radius != 0) {
        forEachParticleInRowSegment(gridY + radius, gridX - radius, gridX + radius, action)
    }

    // лево и право без углов
    for (dy in -radius + 1 until radius) {
        forEachParticleAt(gridX - radius, gridY + dy, action)
        if (radius != 0) {
            forEachParticleAt(gridX + radius, gridY + dy, action)
        }
    }
}
