package io.github.some_example_name.old.cells.base

import io.github.some_example_name.old.core.DISimulationContainer.cellEntity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin


val formulaType = arrayOf(
    "y = ax + b",
    "y = c * sin(ax + b)",
    "y = c * cos(ax + b)",
    "y = sigmoid(ax + b) + c",
    "y = b, x <= a; y = c, x > a",
    "y = b, x < a; y = c, x >= a",
    "y = t",
    "y = impulse(a), x>=1",
    "y = x in (a, b) else y = c",
    "y = x^(a)",
    "y = remember(x), x > 0",
    "y = random(a, b)",
    "y = r(x)",
    "y = g(x)",
    "y = b(x)",
    "y = energy",
    "y = controller(a)",
    "y = decay(x - a), (b, c)",
    "y = spike(x) = 1, dx >= a",
    "y = |x|",
    "y = links amount"
)

fun activation(cellIndex: Int, nonSafeX: Float) = with(cellEntity) {
    val x = when {
        nonSafeX.isNaN() -> 0f
        nonSafeX == Float.POSITIVE_INFINITY -> 1e10f
        nonSafeX == Float.NEGATIVE_INFINITY -> -1e10f
        else -> nonSafeX.coerceIn(-1e10f, 1e10f)
    }

    val y = when (getActivationFuncType(cellIndex)) {
        0 -> getA(cellIndex) * x + getB(cellIndex)
        1 -> getC(cellIndex) * sin(getA(cellIndex) * x + getB(cellIndex))
        2 -> getC(cellIndex) * cos(getA(cellIndex) * x + getB(cellIndex))
        3 -> 1f / (1f + exp(-(getA(cellIndex) * x + getB(cellIndex)))) + getC(cellIndex)
        4 -> if (x <= getA(cellIndex)) getB(cellIndex) else getC(cellIndex)
        5 -> if (x < getA(cellIndex)) getB(cellIndex) else getC(cellIndex)
        6 -> simulationData.timeSimulation + cellIndex
        7 -> {
            if (x >= 1f && simulationData.timeSimulation > getDTime(cellIndex)) {
                setDTime(cellIndex, simulationData.timeSimulation + getA(cellIndex))
            }

            if (simulationData.timeSimulation < getDTime(cellIndex)) {
                1f
            } else {
                setDTime(cellIndex, -1f)
                0f
            }
        }

        8 -> {
            if (x > getA(cellIndex) && x < getB(cellIndex)) {
                x
            } else getC(cellIndex)
        }

        9 -> x.pow(getA(cellIndex))

        10 -> {
            if (x > 0) {
                setRemember(cellIndex, x)
            } else if (x <= 0) {
                setRemember(cellIndex, 0.0f)
            }
            getRemember(cellIndex)
        }

        11 -> {
            val a = getA(cellIndex)
            val b = getB(cellIndex)
            randomFromFloat(x, a, b)
        }

        12 -> (x * 255f).toInt().toFloat()

        13 -> ((x * 255f).toInt() * 256).toFloat()

        14 -> ((x * 255f).toInt() * 65536).toFloat()

        15 -> energy[cellIndex] / maxEnergy[cellIndex]

        16 -> if (simulationData.controllerKeyTouched[getA(cellIndex).toInt()]) 1f else 0f + x

        17 -> {
            val decay = getA(cellIndex)
            val min = getB(cellIndex)
            val max = getC(cellIndex)

            if (x > min && x > getRemember(cellIndex)) {
                setRemember(cellIndex, x.coerceIn(min, max))
            }
            var impulse = getRemember(cellIndex)

            if (impulse > min) {
                impulse -= decay
            }
            impulse = impulse.coerceIn(min, max)

            setRemember(cellIndex, impulse)

            impulse
        }

        18 -> {
            val dx = x - getRemember(cellIndex)
            setRemember(cellIndex, x)

            if (dx > 0 && dx >= getA(cellIndex)) {
                setDTime(cellIndex, simulationData.tickCounter.toFloat())
                1f
            } else 0f
        }

        19 -> abs(x)

        20 -> linkAmount[cellIndex].toFloat()
        else -> x
    }

    val safeY = when {
        y.isNaN() -> 0f
        y == Float.POSITIVE_INFINITY -> 1e10f
        y == Float.NEGATIVE_INFINITY -> -1e10f
        else -> y
    }

    return@with safeY.coerceIn(-1e10f, 1e10f)
}

fun randomFromFloat(seed: Float, min: Float, max: Float): Float {
    var x = seed.toBits()

    x = x xor (x shl 13)
    x = x xor (x shr 17)
    x = x xor (x shl 5)

    val normalized = (x.toUInt().toDouble() / UInt.MAX_VALUE.toDouble()).toFloat()

    return min + normalized * (max - min)
}
