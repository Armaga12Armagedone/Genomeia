package io.github.some_example_name.old.editor.system

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import io.github.some_example_name.old.core.DIGenomeEditorContainer.gridHeight
import io.github.some_example_name.old.core.DIGenomeEditorContainer.gridWidth
import io.github.some_example_name.old.core.utils.findNewOptimalCellPosition
import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.systems.physics.GridManager
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sqrt

private const val AXIAL_LINE_Y = 64f
private const val CELL_RADIUS = 0.5f
private const val EPSILON = 0.001f

class SymmetryManager(
    val gridManager: GridManager,
    val particleEntity: ParticleEntity
) {

    var symmetryMode: SymmetryMode = NoSymmetry



    fun drawSymmetry(shapeRenderer: ShapeRenderer) {
        val symmetryMode = symmetryMode
        when (symmetryMode) {
            Axial -> {
                shapeRenderer.line(0f, gridHeight.toFloat() / 2f, gridWidth.toFloat(), gridHeight.toFloat() / 2f)
            }
            NoSymmetry -> {
                return
            }
            is SquareGrid -> {
                val step = symmetryMode.step
                val ox = symmetryMode.offsetX
                val oy = symmetryMode.offsetY

                // === ВЕРТИКАЛЬНЫЕ ЛИНИИ ===
                var i = floor(((0f - ox) / step)).toInt()   // начинаем с первой линии слева от 0
                while (true) {
                    val x = ox + i * step
                    if (x > gridWidth) break
                    if (x >= 0f) {
                        shapeRenderer.line(x, 0f, x, gridHeight.toFloat())
                    }
                    i++
                }

                // === ГОРИЗОНТАЛЬНЫЕ ЛИНИИ ===
                i = floor(((0f - oy) / step)).toInt()
                while (true) {
                    val y = oy + i * step
                    if (y > gridHeight) break
                    if (y >= 0f) {
                        shapeRenderer.line(0f, y, gridWidth.toFloat(), y)
                    }
                    i++
                }
            }
            is TriangleGrid -> {
                val step = symmetryMode.step
                val ox = symmetryMode.offsetX
                val oy = symmetryMode.offsetY

                val h = step * (sqrt(3f) / 2f)   // высота треугольника

                // === 1. ГОРИЗОНТАЛЬНЫЕ ЛИНИИ === (оставляем как было — они уже корректные)
                var i = floor((0f - oy) / h).toInt()
                while (true) {
                    val y = oy + i * h
                    if (y > gridHeight) break
                    if (y >= 0f) {
                        shapeRenderer.line(0f, y, gridWidth.toFloat(), y)
                    }
                    i++
                }

                // === 2. ДИАГОНАЛЬНЫЕ ЛИНИИ / (60°) ===
                val m1 = sqrt(3f)
                val db1 = step * sqrt(3f)          // правильный шаг по b
                val b0 = oy - m1 * ox              // линия точно проходит через (ox, oy)

                var k = -300                       // начинаем далеко «слева/снизу» (запас для любого размера экрана)
                while (true) {
                    val b = b0 + k * db1
                    val yStart = m1 * 0f + b
                    val yEnd = m1 * gridWidth.toFloat() + b

                    // полностью ниже экрана → продолжаем (ещё не дошли до видимой области)
                    if (yStart < 0f && yEnd < 0f) {
                        k++
                        continue
                    }
                    // полностью выше экрана → можно останавливаться
                    if (yStart > gridHeight && yEnd > gridHeight) break

                    // рисуем полную линию (ShapeRenderer сам обрежет невидимые части)
                    shapeRenderer.line(0f, yStart, gridWidth.toFloat(), yEnd)
                    k++
                }

                // === 3. ДИАГОНАЛЬНЫЕ ЛИНИИ \ (-60°) ===
                val m2 = -sqrt(3f)
                val db2 = step * sqrt(3f)
                val b0_2 = oy - m2 * ox

                k = -300
                while (true) {
                    val b = b0_2 + k * db2
                    val yStart = m2 * 0f + b
                    val yEnd = m2 * gridWidth.toFloat() + b

                    if (yStart < 0f && yEnd < 0f) {
                        k++
                        continue
                    }
                    if (yStart > gridHeight && yEnd > gridHeight) break

                    shapeRenderer.line(0f, yStart, gridWidth.toFloat(), yEnd)
                    k++
                }
            }
        }
    }

    fun snapPosition(x: Float, y: Float): Pair<Float, Float> {
        val symmetryMode = symmetryMode
        return when (symmetryMode) {
            Axial -> {
                val distToLine = abs(y - AXIAL_LINE_Y)

                if (distToLine <= CELL_RADIUS) {
                    Pair(x, AXIAL_LINE_Y)           // прилипание к оси
                } else {
                    Pair(x, 2f * AXIAL_LINE_Y - y)  // зеркалирование в обе стороны
                }
            }
            NoSymmetry -> Pair(x, y)
            is SquareGrid -> {
                val step = symmetryMode.step
                val ox = symmetryMode.offsetX
                val oy = symmetryMode.offsetY

                val resultX = ((x - ox) / step).roundToInt() * step + ox
                val resultY = ((y - oy) / step).roundToInt() * step + oy

                gridManager.getParticles(resultX.toInt(), resultY.toInt()).forEach {
                    if (particleEntity.x[it] == resultX && particleEntity.y[it] == resultY)
                    return Pair(x, y)
                }

                Pair(resultX, resultY)
            }
            is TriangleGrid -> {
                val step = symmetryMode.step
                val ox = symmetryMode.offsetX
                val oy = symmetryMode.offsetY

                val rowHeight = step * (sqrt(3f) / 2f)

                // staggered rows (сдвиг на половину стороны в нечётных рядах)
                val row = round((y - oy) / rowHeight).toLong()
                val resultY = oy + row * rowHeight

                val xShift = if (row % 2L == 0L) 0f else step / 2f

                val resultX = ox + xShift + round((x - ox - xShift) / step) * step

                gridManager.getParticles(resultX.toInt(), resultY.toInt()).forEach {
                    if (particleEntity.x[it] == resultX && particleEntity.y[it] == resultY)
                        return Pair(x, y)
                }

                Pair(resultX, resultY)
            }
        }
    }

    fun newPoint(clickedCell: EditorCell, xs: MutableList<Float>, ys: MutableList<Float>): Pair<Float, Float>? {
        val symmetryMode = symmetryMode

        return when (symmetryMode) {
            Axial -> {
                val cx = clickedCell.x
                val cy = clickedCell.y
                val targetX = 64f
                val targetY = 64f

                // === ЗЕРКАЛЬНАЯ ПОЗИЦИЯ clickedCell ===
                val mirrorY = 2f * AXIAL_LINE_Y - cy
                val mirrorX = cx

                // 8 позиций вокруг ЗЕРКАЛЬНОЙ точки
                val candidates = listOf(
                    Pair(mirrorX - 1f, mirrorY - 1f),
                    Pair(mirrorX,      mirrorY - 1f),
                    Pair(mirrorX + 1f, mirrorY - 1f),
                    Pair(mirrorX - 1f, mirrorY),
                    Pair(mirrorX + 1f, mirrorY),
                    Pair(mirrorX - 1f, mirrorY + 1f),
                    Pair(mirrorX,      mirrorY + 1f),
                    Pair(mirrorX + 1f, mirrorY + 1f)
                )

                var best: Pair<Float, Float>? = null
                var bestDistSq = Float.MAX_VALUE

                for (cand in candidates) {
                    // НЕ вызываем snapPosition снова (зеркало уже сделано выше)
                    val snapped = cand

                    // Пропускаем позицию самой clickedCell
                    if (abs(snapped.first - cx) < EPSILON && abs(snapped.second - cy) < EPSILON) continue

                    // Проверяем занятость
                    if (!isFreePosition(snapped.first, snapped.second)) continue

                    // Выбираем самую близкую к (64, 64)
                    val dx = snapped.first - targetX
                    val dy = snapped.second - targetY
                    val distSq = dx * dx + dy * dy

                    if (distSq < bestDistSq) {
                        bestDistSq = distSq
                        best = snapped
                    }
                }

                if (best != null) return best

                // Fallback
                return findNewOptimalCellPosition(clickedCell.x, clickedCell.y, xs, ys)
            }

            NoSymmetry -> findNewOptimalCellPosition(clickedCell.x, clickedCell.y, xs, ys)

            is SquareGrid -> {
                val step = symmetryMode.step
                val ox = symmetryMode.offsetX
                val oy = symmetryMode.offsetY
                val cx = clickedCell.x
                val cy = clickedCell.y

                // 8 соседних позиций (включая диагонали) — все точно на сетке
                val candidates = listOf(
                    Pair(cx - step, cy - step),
                    Pair(cx,        cy - step),
                    Pair(cx + step, cy - step),
                    Pair(cx - step, cy),
                    Pair(cx + step, cy),
                    Pair(cx - step, cy + step),
                    Pair(cx,        cy + step),
                    Pair(cx + step, cy + step)
                )

                findBestFreePosition(candidates, xs, ys, ox, oy)
            }

            is TriangleGrid -> {
                val step = symmetryMode.step
                val ox = symmetryMode.offsetX
                val oy = symmetryMode.offsetY
                val cx = clickedCell.x
                val cy = clickedCell.y

                val rowHeight = step * (sqrt(3f) / 2f)

                // 6 соседних вершин равностороннего треугольника — все точно на сетке
                val candidates = listOf(
                    Pair(cx + step,         cy),               // 0°
                    Pair(cx - step,         cy),               // 180°
                    Pair(cx + step / 2f,    cy + rowHeight),   // 60°
                    Pair(cx - step / 2f,    cy + rowHeight),   // 120°
                    Pair(cx + step / 2f,    cy - rowHeight),   // -60°
                    Pair(cx - step / 2f,    cy - rowHeight)    // -120°
                )

                findBestFreePosition(candidates, xs, ys, ox, oy)
            }
        }
    }

    private fun findBestFreePosition(
        candidates: List<Pair<Float, Float>>,
        xs: MutableList<Float>,
        ys: MutableList<Float>,
        targetX: Float,
        targetY: Float
    ): Pair<Float, Float>? {
        var best: Pair<Float, Float>? = null
        var bestDistSq = Float.MAX_VALUE

        // tolerance — решает проблему с floating-point в TriangleGrid
        // (sqrt(3) + round + step = 0.6f дают микроскопические расхождения)
        val epsilon = 0.001f   // можно сделать 0.0001f или step * 0.01f

        for (cand in candidates) {
            // === ПРОВЕРКА ЗАНЯТОСТИ ЧЕРЕЗ РАССТОЯНИЕ (а не ==) ===
            var occupied = false
            for (i in xs.indices) {
                if (abs(xs[i] - cand.first) < epsilon && abs(ys[i] - cand.second) < epsilon) {
                    occupied = true
                    break
                }
            }
            if (occupied) continue

            // расстояние до offset (64, 64)
            val dx = cand.first - targetX
            val dy = cand.second - targetY
            val distSq = dx * dx + dy * dy

            if (distSq < bestDistSq) {
                bestDistSq = distSq
                best = cand
            }
        }

        return best
    }


    private fun isFreePosition(x: Float, y: Float): Boolean {
        val ix = x.toInt()
        val iy = y.toInt()

        // Проверяем 9 соседних «ячеек» (3×3) — как просил пользователь
        for (dx in -1..1) {
            for (dy in -1..1) {
                gridManager.getParticles(ix + dx, iy + dy).forEach {
                    if (abs(particleEntity.x[it] - x) < EPSILON && abs(particleEntity.y[it] - y) < EPSILON) {
                        return false
                    }
                }
            }
        }
        return true
    }
}

sealed class SymmetryMode

object NoSymmetry: SymmetryMode()
data class SquareGrid(
    val step: Float,
    val offsetX: Float = 64f,   // ← было 0f, теперь 64f
    val offsetY: Float = 64f    // ← было 0f, теперь 64f
) : SymmetryMode()
object Axial: SymmetryMode()
data class TriangleGrid(
    val step: Float,
    val offsetX: Float = 64f,   // ← было 0f, теперь 64f
    val offsetY: Float = 64f    // ← было 0f, теперь 64f
) : SymmetryMode()
