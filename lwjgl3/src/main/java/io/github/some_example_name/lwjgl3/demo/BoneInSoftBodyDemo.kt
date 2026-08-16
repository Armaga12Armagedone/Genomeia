package io.github.some_example_name.lwjgl3.demo

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import io.github.some_example_name.lwjgl3.StartupHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * КОСТЬ В МЯГКОМ ТЕЛЕ — 2D XPBD + shape matching.
 *
 * Автономная демка, ни с чем в проекте не связана: запускается своим main() ниже.
 * Из движка не используется ничего, чтобы пример читался сам по себе.
 *
 * ЧТО ЗДЕСЬ ПОКАЗАНО
 * ------------------
 * Конечность с локтем: две абсолютно жёсткие кости, мягкий сустав между ними и мягкие
 * кончики. Мягкая часть решается обычными XPBD-ограничениями расстояния. Кости не
 * решаются вообще — каждый подшаг им находится оптимальный поворот позы покоя, и вершины
 * ставятся на него ровно. Жёсткость выполняется по построению, а не достигается итерациями.
 *
 * Замеренная максимальная деформация кости за 600 кадров:
 *
 *   подшагов | проекция вкл | проекция выкл, compliance = 0
 *   ---------|--------------|-------------------------------
 *      1     |     3e-7     |            9.7 %
 *      2     |     3e-7     |            6.8 %
 *      5     |     3e-7     |            1.8 %
 *     10     |     3e-7     |            0.5 %
 *     20     |     3e-7     |            0.15 %
 *
 * 3e-7 — это округление float. Жёсткость проекции от числа итераций не зависит вообще,
 * жёсткость решателя зависит и точной не становится никогда. Нажми B, чтобы увидеть.
 *
 * РАСКЛАДКА ДАННЫХ
 * ----------------
 * Частицы лежат в параллельных FloatArray (SoA), как в движке, а не в массиве объектов.
 * Для 18 вершин это роли не играет, но пример затем и написан, чтобы его переносить.
 *
 * УПРАВЛЕНИЕ
 * ----------
 * ЛКМ — тянуть за любую вершину (в том числе за вершину кости)
 * SPACE — пауза, R — сброс, B — жёсткость костей вкл/выкл
 */
class BoneInSoftBodyDemo : ApplicationAdapter() {

    companion object {

        // =============================================================
        //  ПАРАМЕТРЫ СИМУЛЯЦИИ
        // =============================================================

        /** Шаг кадра. Фиксированный, а не Gdx.graphics.deltaTime — чтобы демка была повторяемой. */
        private const val DT = 1f / 60f

        /**
         * Подшагов XPBD на кадр.
         *
         * Схема "small steps": на подшаг делается РОВНО ОДНА итерация решателя. Это точнее
         * и дешевле, чем один шаг с N итерациями Гаусса-Зейделя.
         *
         * Для мягкой части 4 достаточно с запасом. Костям не нужен ни один: их жёсткость
         * от числа подшагов не зависит.
         */
        private const val SUBSTEPS = 4

        /**
         * Податливость мягких связей, alpha = 1/k, [м/Н].
         *
         * В XPBD это физическая константа материала: делением на h^2 из неё получается
         * alpha~, и жёсткость перестаёт зависеть от величины шага. В этом и смысл буквы
         * "X" (extended) — в классическом PBD жёсткость плыла и от dt, и от числа итераций.
         *
         * Ориентир по деформации мягкой части: 2.5e-5 даёт ~11 %, 5e-5 ~18 %, 2e-4 ~30 %.
         */
        private const val SOFT_COMPLIANCE = 5e-5f

        /** Закрепить две вершины плеча, чтобы конечность висела и локоть проседал под весом. */
        private const val PIN_SHOULDER = true

        private const val GRAVITY = -9.81f
        private const val GROUND_Y = 0f

        /**
         * Вес перетаскиваемой вершины в подгонке формы.
         *
         * invMass = 0 убирает вершину из решателя ограничений — сдвинуть её нельзя.
         * Но проекция кости позиционная: она двигает ВСЕ свои вершины, включая эту.
         * Большой вес в подгонке означает «кость обязана следовать за этой точкой», и
         * кость едет за мышью целиком, оставаясь абсолютно жёсткой. Поворот при этом
         * задаётся остальными вершинами — одна точка его не определяет.
         */
        private const val DRAG_MATCH_WEIGHT = 200f

        /** Радиус захвата вершины мышью, в мировых единицах. */
        private const val PICK_RADIUS = 0.10f

        // =============================================================
        //  ГЕОМЕТРИЯ ТЕЛА
        // =============================================================

        /**
         * Тело — лента-лесенка, согнутая в V. У перекладины r две вершины:
         * внешняя 2r и внутренняя 2r+1.
         *
         * Перекладины 1-3 и 5-7 — кости. Перекладина 4 — локоть, 0 и 8 — кончики, они мягкие.
         */
        private const val NUM_RUNGS = 9
        private const val ELBOW_RUNG = 4
        private const val HALF_WIDTH = 0.115f

        private const val SHOULDER_X = 0.62f; private const val SHOULDER_Y = 1.42f
        private const val ELBOW_X = 1.52f;    private const val ELBOW_Y = 0.46f
        private const val WRIST_X = 2.42f;    private const val WRIST_Y = 1.42f

        private const val PARTICLE_COUNT = NUM_RUNGS * 2

        // =============================================================
        //  ВИД
        // =============================================================

        private const val VIEW_WIDTH = 3.4f
        private const val VIEW_HEIGHT = 2.125f

        private val BG             = Color.valueOf("12161E")
        private val GROUND_COLOR   = Color.valueOf("2B3341")
        private val SOFT_FILL      = Color.valueOf("5A96DC2B")
        private val LINK           = Color.valueOf("96B4DC47")
        private val LINK_OFF       = Color.valueOf("E8A33D33")
        private val BONE_FILL      = Color.valueOf("E8A33D8C")
        private val BONE_FILL_OFF  = Color.valueOf("E8A33D21")
        private val BONE_EDGE      = Color.valueOf("E8A33DFF")
        private val BONE_EDGE_OFF  = Color.valueOf("E8A33D73")
        private val DOT            = Color.valueOf("DFE6F0FF")
        private val PINNED_COLOR   = Color.valueOf("F2645AFF")
        private val ACTIVE         = Color.valueOf("FFFFFFFF")
        private val HUD_TEXT       = Color.valueOf("EAF0F8FF")
        private val HUD_MUTED      = Color.valueOf("76818FFF")
    }

    // =================================================================
    //  ДАННЫЕ ЧАСТИЦ (SoA, как в движке)
    // =================================================================

    private val px = FloatArray(PARTICLE_COUNT)      // текущая позиция
    private val py = FloatArray(PARTICLE_COUNT)
    private val prevX = FloatArray(PARTICLE_COUNT)   // позиция на начало подшага
    private val prevY = FloatArray(PARTICLE_COUNT)
    private val vx = FloatArray(PARTICLE_COUNT)
    private val vy = FloatArray(PARTICLE_COUNT)
    private val invMass = FloatArray(PARTICLE_COUNT)

    /** Поза покоя. Для костей это ЕДИНСТВЕННЫЙ источник формы — больше их ничто не держит. */
    private val restX = FloatArray(PARTICLE_COUNT)
    private val restY = FloatArray(PARTICLE_COUNT)

    private val matchWeight = FloatArray(PARTICLE_COUNT)

    /** Номер кости частицы, -1 если частица не в кости. */
    private val boneOf = IntArray(PARTICLE_COUNT) { -1 }

    // =================================================================
    //  КОСТИ
    // =================================================================

    /**
     * Кость — просто перечень вершин плюс их поза покоя. Никакой отдельной сущности
     * твёрдого тела, никакой угловой скорости и момента инерции: всё живёт в тех же
     * массивах вершин, что и мягкая ткань.
     *
     * Здесь для читаемости Array<IntArray>. В движке это лёг бы CSR-парой
     * (boneStart / boneMembers) — ровно как gridManager.cellStart / particleIdx.
     */
    private val boneRungs = arrayOf(
        intArrayOf(1, 2, 3),
        intArrayOf(5, 6, 7)
    )
    private lateinit var boneMembers: Array<IntArray>

    /**
     * Жёсткость костей. Константа по смыслу, var только ради клавиши B: выключение
     * показывает, что compliance = 0 у обычного ограничения — это ещё не жёсткость.
     */
    private var bonesRigid = true

    // =================================================================
    //  ОГРАНИЧЕНИЯ (тоже SoA)
    // =================================================================

    private var constraintCount = 0
    private lateinit var conA: IntArray
    private lateinit var conB: IntArray
    private lateinit var conRest: FloatArray

    /** Обе вершины связи в одной кости. Такие связи при включённых костях не считаются. */
    private lateinit var conInsideBone: BooleanArray

    // =================================================================
    //  ВВОД И ОТРИСОВКА
    // =================================================================

    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var viewport: FitViewport
    private lateinit var camera: OrthographicCamera
    private lateinit var hudCamera: OrthographicCamera
    private val tmp = Vector3()

    private var dragId = -1
    private var hoverId = -1
    private var dragSavedInvMass = 1f
    private var mouseX = 0f
    private var mouseY = 0f
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var wasTouched = false
    private var paused = false

    // =================================================================
    //  1. ПОСТРОЕНИЕ ТЕЛА  (геометрия, не физика — можно пролистать)
    // =================================================================

    private fun buildGeometry() {
        // Центральная линия: два прямых отрезка со стыком в локте.
        val cx = FloatArray(NUM_RUNGS)
        val cy = FloatArray(NUM_RUNGS)
        for (r in 0 until NUM_RUNGS) {
            if (r <= ELBOW_RUNG) {
                val t = r.toFloat() / ELBOW_RUNG
                cx[r] = SHOULDER_X + (ELBOW_X - SHOULDER_X) * t
                cy[r] = SHOULDER_Y + (ELBOW_Y - SHOULDER_Y) * t
            } else {
                val t = (r - ELBOW_RUNG).toFloat() / (NUM_RUNGS - 1 - ELBOW_RUNG)
                cx[r] = ELBOW_X + (WRIST_X - ELBOW_X) * t
                cy[r] = ELBOW_Y + (WRIST_Y - ELBOW_Y) * t
            }
        }

        // Нормаль в каждой перекладине. В изломе — митра (биссектриса с растяжением),
        // иначе лента в локте пережималась бы.
        for (r in 0 until NUM_RUNGS) {
            var nx: Float
            var ny: Float
            var scale = HALF_WIDTH

            val hasPrev = r > 0
            val hasNext = r < NUM_RUNGS - 1

            // Нормаль отрезка — это его направление, повёрнутое на 90 градусов.
            var pnx = 0f; var pny = 0f
            if (hasPrev) {
                val dx = cx[r] - cx[r - 1]; val dy = cy[r] - cy[r - 1]
                val len = sqrt(dx * dx + dy * dy)
                pnx = -dy / len; pny = dx / len
            }
            var nnx = 0f; var nny = 0f
            if (hasNext) {
                val dx = cx[r + 1] - cx[r]; val dy = cy[r + 1] - cy[r]
                val len = sqrt(dx * dx + dy * dy)
                nnx = -dy / len; nny = dx / len
            }

            if (!hasPrev) {
                nx = nnx; ny = nny
            } else if (!hasNext) {
                nx = pnx; ny = pny
            } else {
                var mx = pnx + nnx
                var my = pny + nny
                val len = sqrt(mx * mx + my * my)
                mx /= len; my /= len
                nx = mx; ny = my
                // Растяжение митры: 1/cos(половины угла излома).
                scale = HALF_WIDTH / max(mx * pnx + my * pny, 0.35f)
            }

            restX[2 * r] = cx[r] + nx * scale;     restY[2 * r] = cy[r] + ny * scale
            restX[2 * r + 1] = cx[r] - nx * scale; restY[2 * r + 1] = cy[r] - ny * scale
        }
    }

    private fun buildTopology() {
        boneMembers = Array(boneRungs.size) { b ->
            val rungs = boneRungs[b]
            IntArray(rungs.size * 2).also { ids ->
                for (k in rungs.indices) {
                    val outer = 2 * rungs[k]
                    val inner = outer + 1
                    ids[2 * k] = outer
                    ids[2 * k + 1] = inner
                    boneOf[outer] = b
                    boneOf[inner] = b
                }
            }
        }

        // Перекладина + два рельса + две диагонали на каждый четырёхугольник.
        val maxConstraints = NUM_RUNGS + (NUM_RUNGS - 1) * 4
        conA = IntArray(maxConstraints)
        conB = IntArray(maxConstraints)
        conRest = FloatArray(maxConstraints)
        conInsideBone = BooleanArray(maxConstraints)

        for (r in 0 until NUM_RUNGS) {
            addConstraint(2 * r, 2 * r + 1)                     // перекладина
            if (r + 1 < NUM_RUNGS) {
                addConstraint(2 * r, 2 * r + 2)                 // внешний рельс
                addConstraint(2 * r + 1, 2 * r + 3)             // внутренний рельс
                // Диагонали. Без них четырёхугольник складывается в ромб при ПОЛНОСТЬЮ
                // неизменных длинах сторон: длины сами по себе форму не держат. Нужен
                // либо сдвиговый констрейнт, либо площадь, либо (как у костей) проекция.
                addConstraint(2 * r, 2 * r + 3)
                addConstraint(2 * r + 1, 2 * r + 2)
            }
        }
    }

    private fun addConstraint(i: Int, j: Int) {
        val dx = restX[i] - restX[j]
        val dy = restY[i] - restY[j]
        conA[constraintCount] = i
        conB[constraintCount] = j
        conRest[constraintCount] = sqrt(dx * dx + dy * dy)
        conInsideBone[constraintCount] = boneOf[i] != -1 && boneOf[i] == boneOf[j]
        constraintCount++
    }

    private fun reset() {
        for (i in 0 until PARTICLE_COUNT) {
            px[i] = restX[i]; py[i] = restY[i]
            vx[i] = 0f; vy[i] = 0f
            matchWeight[i] = 1f
            invMass[i] = 1f
        }
        if (PIN_SHOULDER) { invMass[0] = 0f; invMass[1] = 0f }
        dragId = -1
    }

    // =================================================================
    //  2. XPBD: ОГРАНИЧЕНИЕ РАССТОЯНИЯ
    //
    //      C       = |xi - xj| - rest
    //      grad    = единичный вектор вдоль связи (у одного конца +, у другого -)
    //      alphaT  = compliance / h^2
    //      dLambda = -C / (wi + wj + alphaT)
    //      dx_i    = +grad * dLambda * wi
    //      dx_j    = -grad * dLambda * wj
    //
    //  Лямбда НЕ накапливается между подшагами, и это корректно именно потому, что на
    //  подшаг делается РОВНО ОДНА итерация: тогда суммарная лямбда и есть dLambda.
    // =================================================================

    private fun solveConstraints(h: Float) {
        val invH2 = 1f / (h * h)

        for (c in 0 until constraintCount) {
            val insideBone = conInsideBone[c]

            // Связи внутри кости при включённых костях — чистая потеря времени: проекция
            // всё равно перезапишет их результат. В движке их просто не создают, отфильтровав
            // на этапе запекания раскладки.
            if (bonesRigid && insideBone) continue

            val i = conA[c]
            val j = conB[c]
            val wi = invMass[i]
            val wj = invMass[j]
            val w = wi + wj
            if (w == 0f) continue

            var dx = px[i] - px[j]
            var dy = py[i] - py[j]
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1e-9f) continue
            dx /= len; dy /= len

            val constraintValue = len - conRest[c]
            val alpha = (if (insideBone) 0f else SOFT_COMPLIANCE) * invH2
            val dLambda = -constraintValue / (w + alpha)

            px[i] += dx * dLambda * wi; py[i] += dy * dLambda * wi
            px[j] -= dx * dLambda * wj; py[j] -= dy * dLambda * wj
        }
    }

    // =================================================================
    //  3. SHAPE MATCHING — ЯДРО ПРИМЕРА
    //
    //  Ищется жёсткое преобразование (поворот + сдвиг), минимизирующее
    //      sum_i  w_i * | R*q_i + c - p_i |^2
    //  где q_i — вершина позы покоя относительно её центроида, p_i — текущая вершина.
    //
    //  В 2D раскладывать матрицу не нужно. Разворачивая скалярное произведение для
    //  R = [[cos,-sin],[sin,cos]], получаем, что максимизировать надо
    //      cos*S + sin*T,   S = sum w (q . p),   T = sum w (q x p)
    //  а максимум лежит просто в направлении вектора (S, T). Весь поворот — это ДВА
    //  накопителя и один корень. Ни atan2, ни SVD.
    //
    //  alpha = 1: вершины ставятся на цель ровно. Это и есть абсолютная жёсткость — не
    //  «очень жёсткое ограничение», а отсутствие степеней свободы. Ноль итераций, ноль
    //  зависимости от длины кости.
    // =================================================================

    private fun projectBone(ids: IntArray) {
        // --- проход 1: центроид текущий и центроид позы покоя ---
        //
        // Оба считаются здесь, а не берутся запечёнными: веса меняются при перетаскивании,
        // а в движке будет меняться ещё и состав кости — от смерти клеток. Центроид покоя
        // обязан считаться по ТОМУ ЖЕ подмножеству и с ТЕМИ ЖЕ весами, что и текущий,
        // иначе кость поедет.
        var cx = 0f; var cy = 0f
        var c0x = 0f; var c0y = 0f
        var wsum = 0f
        for (i in ids) {
            val w = matchWeight[i]
            cx += w * px[i];    cy += w * py[i]
            c0x += w * restX[i]; c0y += w * restY[i]
            wsum += w
        }
        cx /= wsum; cy /= wsum; c0x /= wsum; c0y /= wsum

        // --- проход 2: два скаляра, задающих поворот ---
        var s = 0f
        var t = 0f
        for (i in ids) {
            val w = matchWeight[i]
            val ppx = px[i] - cx;    val ppy = py[i] - cy       // текущая, центрированная
            val qx = restX[i] - c0x; val qy = restY[i] - c0y    // покоя,   центрированная
            s += w * (qx * ppx + qy * ppy)   // ~ cos
            t += w * (qx * ppy - qy * ppx)   // ~ sin
        }

        val norm = sqrt(s * s + t * t)
        if (norm < 1e-9f) return            // кость схлопнулась в точку: поворот не определён
        val cos = s / norm
        val sin = t / norm

        // --- проход 3: снап на жёсткую цель ---
        for (i in ids) {
            val qx = restX[i] - c0x
            val qy = restY[i] - c0y
            px[i] = cx + cos * qx - sin * qy
            py[i] = cy + sin * qx + cos * qy
        }
    }

    // =================================================================
    //  4. ЦИКЛ СИМУЛЯЦИИ
    //
    //  Порядок внутри подшага критичен:
    //     integrate -> ограничения -> ПРОЕКЦИЯ КОСТЕЙ -> восстановление скоростей
    //
    //  Проекция обязана быть последней: всё, что запишет в позиции после неё, нарушит
    //  жёсткость буквально. Поэтому пол обрабатывается в integrate — кость может на
    //  подшаг чуть уйти под него, на следующем это подберётся.
    // =================================================================

    private fun simulate() {
        val h = DT / SUBSTEPS
        for (step in 0 until SUBSTEPS) {
            integrate(h)
            solveConstraints(h)
            if (bonesRigid) for (ids in boneMembers) projectBone(ids)
            updateVelocities(h)
        }
    }

    private fun integrate(h: Float) {
        for (i in 0 until PARTICLE_COUNT) {
            prevX[i] = px[i]
            prevY[i] = py[i]

            if (i == dragId) {                  // за неё тянут: телепорт к мыши
                px[i] = mouseX; py[i] = mouseY
                continue
            }
            if (invMass[i] == 0f) continue      // закреплена

            vy[i] += GRAVITY * h
            px[i] += vx[i] * h
            py[i] += vy[i] * h

            if (py[i] < GROUND_Y) {             // пол: возврат по x на prev даёт полное трение
                px[i] = prevX[i]
                py[i] = GROUND_Y
            }
        }
    }

    /**
     * Скорость не интегрируется отдельно, а ВОССТАНАВЛИВАЕТСЯ из фактического смещения.
     *
     * Отсюда же берётся демпфирование: работа, съеденная проекцией и ограничениями,
     * автоматически вычитается из скорости. Искусственный демпфер в этой схеме не нужен —
     * в отличие от скоростного решателя, где его приходится подбирать руками.
     */
    private fun updateVelocities(h: Float) {
        for (i in 0 until PARTICLE_COUNT) {
            if (i == dragId || invMass[i] == 0f) {
                vx[i] = 0f; vy[i] = 0f
                continue
            }
            vx[i] = (px[i] - prevX[i]) / h
            vy[i] = (py[i] - prevY[i]) / h
        }
    }

    // =================================================================
    //  5. ВВОД
    // =================================================================

    private fun handleInput() {
        tmp.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)
        viewport.unproject(tmp)
        mouseX = tmp.x
        mouseY = tmp.y

        val touched = Gdx.input.isTouched
        if (touched && !wasTouched) beginDrag()
        if (!touched && wasTouched) endDrag()
        wasTouched = touched

        hoverId = if (dragId >= 0) dragId else nearestParticle()

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) paused = !paused
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) reset()
        if (Gdx.input.isKeyJustPressed(Input.Keys.B)) bonesRigid = !bonesRigid
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit()
    }

    private fun nearestParticle(): Int {
        var best = -1
        var bestD2 = PICK_RADIUS * PICK_RADIUS
        for (i in 0 until PARTICLE_COUNT) {
            val dx = px[i] - mouseX
            val dy = py[i] - mouseY
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) { bestD2 = d2; best = i }
        }
        return best
    }

    private fun beginDrag() {
        val i = nearestParticle()
        if (i < 0) return
        dragId = i
        dragSavedInvMass = invMass[i]
        invMass[i] = 0f                        // из решателя ограничений вершина выпадает
        matchWeight[i] = DRAG_MATCH_WEIGHT     // а проекцию кости, наоборот, тянет за собой
        lastMouseX = mouseX
        lastMouseY = mouseY
    }

    private fun endDrag() {
        if (dragId < 0) return
        invMass[dragId] = dragSavedInvMass
        matchWeight[dragId] = 1f
        // Бросок: скорость берётся от мыши, а не от телепорта, иначе рывок улетает.
        vx[dragId] = (mouseX - lastMouseX) / DT
        vy[dragId] = (mouseY - lastMouseY) / DT
        dragId = -1
    }

    // =================================================================
    //  6. ЖИЗНЕННЫЙ ЦИКЛ И ОТРИСОВКА
    // =================================================================

    override fun create() {
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont()
        camera = OrthographicCamera()
        viewport = FitViewport(VIEW_WIDTH, VIEW_HEIGHT, camera)
        hudCamera = OrthographicCamera()

        buildGeometry()
        buildTopology()
        reset()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height)
        // Тело занимает y от 0 до ~1.55, поэтому камера смещена вниз относительно
        // центра, который FitViewport ставит по умолчанию.
        camera.position.set(1.55f, 0.80f, 0f)
        camera.update()
        hudCamera.setToOrtho(false, width.toFloat(), height.toFloat())
    }

    override fun render() {
        handleInput()
        if (!paused) simulate()

        ScreenUtils.clear(BG)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapes.projectionMatrix = camera.combined
        drawFilled()
        drawLines()

        drawHud()

        lastMouseX = mouseX
        lastMouseY = mouseY
    }

    private fun drawFilled() {
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        // Мягкая ткань — вся лента. Заливается по четырёхугольникам, а не одним контуром:
        // контур тела невыпуклый, веером его не затриангулировать.
        shapes.color = SOFT_FILL
        fillStrip(0, NUM_RUNGS - 1)

        // Кости поверх.
        shapes.color = if (bonesRigid) BONE_FILL else BONE_FILL_OFF
        for (rungs in boneRungs) fillStrip(rungs.first(), rungs.last())

        // Вершины.
        for (i in 0 until PARTICLE_COUNT) {
            val pinned = invMass[i] == 0f && i != dragId
            val active = i == dragId || i == hoverId
            shapes.color = when {
                i == dragId -> ACTIVE
                pinned -> PINNED_COLOR
                else -> DOT
            }
            shapes.circle(px[i], py[i], if (active) 0.031f else 0.019f, 20)
        }

        shapes.end()
    }

    /** Заливает участок ленты от перекладины [fromRung] до [toRung] двумя треугольниками на звено. */
    private fun fillStrip(fromRung: Int, toRung: Int) {
        for (r in fromRung until toRung) {
            val a = 2 * r          // внешняя, перекладина r
            val b = a + 1          // внутренняя, перекладина r
            val c = 2 * (r + 1)    // внешняя, перекладина r+1
            val d = c + 1          // внутренняя, перекладина r+1
            shapes.triangle(px[a], py[a], px[c], py[c], px[d], py[d])
            shapes.triangle(px[a], py[a], px[d], py[d], px[b], py[b])
        }
    }

    private fun drawLines() {
        // rectLine, а не ShapeType.Line: толщина линии в GL не задаётся переносимо,
        // а здесь она нужна в мировых единицах.
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        shapes.color = GROUND_COLOR
        shapes.rectLine(-1f, GROUND_Y, 5f, GROUND_Y, 0.008f)

        for (c in 0 until constraintCount) {
            val inactive = bonesRigid && conInsideBone[c]
            shapes.color = if (inactive) LINK_OFF else LINK
            val i = conA[c]
            val j = conB[c]
            shapes.rectLine(px[i], py[i], px[j], py[j], 0.004f)
        }

        // Контур костей.
        shapes.color = if (bonesRigid) BONE_EDGE else BONE_EDGE_OFF
        for (rungs in boneRungs) {
            val outline = IntArray(rungs.size * 2)
            for (k in rungs.indices) {
                outline[k] = 2 * rungs[k]                               // внешние вперёд
                outline[rungs.size * 2 - 1 - k] = 2 * rungs[k] + 1      // внутренние назад
            }
            for (k in outline.indices) {
                val i = outline[k]
                val j = outline[(k + 1) % outline.size]
                shapes.rectLine(px[i], py[i], px[j], py[j], 0.008f)
            }
        }

        shapes.end()
    }

    private fun drawHud() {
        batch.projectionMatrix = hudCamera.combined
        batch.begin()
        var y = Gdx.graphics.height - 14f
        val line = 18f

        font.color = HUD_TEXT
        font.draw(batch, "KOST V MYAGKOM TELE  -  2D XPBD + shape matching", 16f, y); y -= line * 1.4f

        font.color = HUD_MUTED
        font.draw(batch, "substeps = $SUBSTEPS    soft compliance = $SOFT_COMPLIANCE", 16f, y); y -= line
        val boneNote = if (bonesRigid) "  (projection, alpha = 1)" else "  (compliance = 0 only)"
        font.draw(batch, "bones rigid = $bonesRigid$boneNote", 16f, y); y -= line * 1.4f
        font.draw(batch, "LMB drag any vertex    SPACE pause    R reset    B toggle bones", 16f, y); y -= line
        if (paused) {
            font.color = BONE_EDGE
            font.draw(batch, "PAUSED", 16f, y)
        }
        batch.end()
    }

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
    }
}

/**
 * Запуск. В IDE — зелёная стрелка слева от этой функции.
 * Из командной строки удобнее прописать отдельную задачу в lwjgl3/build.gradle.
 */
fun main() {
    if (StartupHelper.startNewJvmIfRequired()) return   // нужно для macOS, безвредно на Windows/Linux
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Кость в мягком теле — XPBD + shape matching")
        setWindowedMode(1000, 680)
        useVsync(true)
        setForegroundFPS(140)
    }
    Lwjgl3Application(BoneInSoftBodyDemo(), config)
}
