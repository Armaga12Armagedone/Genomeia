package io.github.some_example_name.old.cells.base

import io.github.some_example_name.old.entities.CellEntity


/**
 * Выход нейрона по входу: формула клетки (Formulas) с защитой от NaN и бесконечностей
 * на входе и на выходе.
 */
fun CellEntity.activation(cellIndex: Int, nonSafeX: Float): Float {
    val x = when {
        nonSafeX.isNaN() -> 0f
        nonSafeX == Float.POSITIVE_INFINITY -> 1e10f
        nonSafeX == Float.NEGATIVE_INFINITY -> -1e10f
        else -> nonSafeX.coerceIn(-1e10f, 1e10f)
    }

    val y = formulas[getActivationFuncType(cellIndex)].compute(this, cellIndex, x)

    val safeY = when {
        y.isNaN() -> 0f
        y == Float.POSITIVE_INFINITY -> 1e10f
        y == Float.NEGATIVE_INFINITY -> -1e10f
        else -> y
    }

    return safeY.coerceIn(-1e10f, 1e10f)
}

fun randomFromFloat(seed: Float, min: Float, max: Float): Float {
    var x = seed.toBits()

    x = x xor (x shl 13)
    x = x xor (x shr 17)
    x = x xor (x shl 5)

    val normalized = (x.toUInt().toDouble() / UInt.MAX_VALUE.toDouble()).toFloat()

    return min + normalized * (max - min)
}
