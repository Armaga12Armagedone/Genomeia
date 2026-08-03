package io.github.some_example_name.old.systems.physics
import io.github.some_example_name.old.core.DIContext
import io.github.some_example_name.old.core.WorldResizable
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList

class GridManager (
    var gridWidth: Int,
    var gridHeight: Int,
    val diContext: DIContext,
    val maxAmountOfParticles: Int
): WorldResizable {
    var gridSize = gridWidth * gridHeight
    var grid = IntArray(gridSize * maxAmountOfParticles) { -1 }
    var particleCounts = IntArray(gridSize)
    var mapMoreThenMax = Array(diContext.totalChunks * 2) { Int2ObjectOpenHashMap<IntArrayList>() }

    private var halfChunkSize = diContext.chunkSize / 2

    /**
     * Сдвиг вместо деления, если halfChunkSize — степень двойки, иначе -1.
     * idiv на x86 — это 20-40 циклов, он не пайплайнится и блокирует divider;
     * сдвиг — 1 цикл. halfChunkSize = gridSize / (threadCount * 2) / 2 и степенью
     * двойки быть не обязан (зависит от размеров мира), поэтому fallback сохранён.
     */
    private var halfChunkShift = shiftOfPowerOfTwo(halfChunkSize)

    private fun getHalfChunkId(gridIndex: Int): Int {
        val shift = halfChunkShift
        return if (shift >= 0) gridIndex ushr shift else gridIndex / halfChunkSize
    }


    fun addParticle(x: Int, y: Int, value: Int): Int {
        if (x !in 0..<gridWidth || y < 0 || y >= gridHeight) {
            //TODO Запретить спавн клетки за границей сетки
            throw Exception("Out of grid bounds")
        }
        val cellIndex = y * gridWidth + x
        if (particleCounts[cellIndex] >= maxAmountOfParticles) {
            val threadId = getHalfChunkId(cellIndex)
            var list = mapMoreThenMax[threadId].get(cellIndex)
            if (list == null) {
                list = IntArrayList()
                mapMoreThenMax[threadId].put(cellIndex, list)
            }
            list.add(value)
        } else {
            val gridIndex = cellIndex * maxAmountOfParticles + particleCounts[cellIndex]
            grid[gridIndex] = value
        }

        particleCounts[cellIndex]++
        return cellIndex
    }

    fun removeParticle(cellIndex: Int, value: Int): Boolean {
        val start = cellIndex * maxAmountOfParticles
        if (particleCounts[cellIndex] <= maxAmountOfParticles) {
            val end = start + particleCounts[cellIndex] - 1
            for (i in start..end) {
                if (grid[i] == value) {
                    grid[i] = grid[end]
                    grid[end] = -1
                    particleCounts[cellIndex]--
                    return true
                }
            }
        } else {
            val end = start + maxAmountOfParticles - 1
            val threadId = getHalfChunkId(cellIndex)
            val list = mapMoreThenMax[threadId].get(cellIndex)
            for (i in start..end) {
                if (grid[i] == value) {
                    grid[i] = list?.removeInt(list.size - 1) ?: throw Exception("List is null or empty but particleCounts > MAX_AMOUNT_OF_PARTICLES")
                    if (list.isEmpty) {
                        mapMoreThenMax[threadId].remove(cellIndex)
                    }
                    particleCounts[cellIndex]--
                    return true
                }
            }
            if (list?.rem(value) ?: false) {
                particleCounts[cellIndex]--
                if (list.isEmpty) {
                    mapMoreThenMax[threadId].remove(cellIndex)//TODO swap remove without copy array
                }
            } else throw Exception("Couldn't delete list but particleCounts > MAX_AMOUNT_OF_PARTICLES")
            return true
        }

        return false
    }

    // ===================================================================================
    // БЕЗАЛЛОКАЦИОННОЕ ЧТЕНИЕ СЕТКИ
    //
    // Раскладка: обычные частицы клетки лежат в grid[cellIndex * maxAmountOfParticles ..
    // + min(count, maxAmountOfParticles)). Если count > maxAmountOfParticles, "хвост"
    // (count - maxAmountOfParticles штук) лежит в mapMoreThenMax[halfChunk][cellIndex].
    //
    // ВАЖНО: прямой обход валиден только тогда, когда во время обхода сетка не мутирует
    // (фаза коллизий этому условию удовлетворяет: repulse/onContact не вызывают
    // addParticle/removeParticle, всё складывается в отложенные команды).
    // ===================================================================================

    /** Индекс клетки сетки или -1, если координаты вне сетки. */
    fun cellIndexAt(x: Int, y: Int): Int =
        if (x !in 0..<gridWidth || y < 0 || y >= gridHeight) -1 else y * gridWidth + x

    /** Смещение первого слота клетки в массиве [grid]. */
    fun cellSlotStart(cellIndex: Int): Int = cellIndex * maxAmountOfParticles

    /** Есть ли у клетки "хвост" переполнения. */
    fun hasOverflow(cellIndex: Int): Boolean = particleCounts[cellIndex] > maxAmountOfParticles

    /**
     * Список переполнения клетки или null.
     * Публичный, потому что им пользуются публичные inline-функции ниже.
     */
    fun overflowListOrNull(cellIndex: Int): IntArrayList? =
        mapMoreThenMax[getHalfChunkId(cellIndex)].get(cellIndex)

    /** Обход всех частиц клетки без копирования и аллокаций. */
    inline fun forEachParticleInCellIndex(cellIndex: Int, action: (Int) -> Unit) {
        val count = particleCounts[cellIndex]
        if (count <= 0) return

        val cells = grid
        val start = cellIndex * maxAmountOfParticles

        if (count <= maxAmountOfParticles) {
            for (i in start until start + count) action(cells[i])
            return
        }

        val extraList = overflowListOrNull(cellIndex)
            ?: throw IllegalStateException("Overflow list is null but particleCounts > maxAmountOfParticles")
        val extraElements = extraList.elements()
        val extraSize = extraList.size
        for (i in 0 until extraSize) action(extraElements[i])
        for (i in start until start + maxAmountOfParticles) action(cells[i])
    }

    /** Обход всех частиц клетки по координатам. Вне сетки — просто ничего не делает. */
    inline fun forEachParticleAt(x: Int, y: Int, action: (Int) -> Unit) {
        val cellIndex = cellIndexAt(x, y)
        if (cellIndex < 0) return
        forEachParticleInCellIndex(cellIndex, action)
    }

    /**
     * Обход частиц горизонтального отрезка одного ряда [xFrom-xTo] (границы клампятся).
     * Дешевле, чем несколько вызовов forEachParticleAt: индекс клетки инкрементируется.
     */
    inline fun forEachParticleInRowSegment(y: Int, xFrom: Int, xTo: Int, action: (Int) -> Unit) {
        if (y !in 0..<gridHeight) return
        val from = if (xFrom < 0) 0 else xFrom
        val to = if (xTo >= gridWidth) gridWidth - 1 else xTo
        if (from > to) return
        val rowBase = y * gridWidth
        for (cellIndex in rowBase + from..rowBase + to) {
            forEachParticleInCellIndex(cellIndex, action)
        }
    }

    /**
     * Копирует частицы клетки в переиспользуемый буфер [dst], возвращает количество.
     * Нужна там, где требуется случайный доступ (например, попарный перебор i<j).
     */
    fun copyParticlesInto(cellIndex: Int, dst: IntArray): Int {
        val count = particleCounts[cellIndex]
        if (count <= 0) return 0
        if (dst.size < count) throw IllegalArgumentException("dst is too small: ${dst.size} < $count")

        val start = cellIndex * maxAmountOfParticles
        if (count <= maxAmountOfParticles) {
            System.arraycopy(grid, start, dst, 0, count)
        } else {
            val extraList = overflowListOrNull(cellIndex)
                ?: throw IllegalStateException("Overflow list is null but particleCounts > maxAmountOfParticles")
            val extraSize = extraList.size
            if (extraSize > 0) System.arraycopy(extraList.elements(), 0, dst, 0, extraSize)
            System.arraycopy(grid, start, dst, extraSize, maxAmountOfParticles)
        }
        return count
    }

    /**
     * АЛЛОЦИРУЕТ новый массив. Не использовать в горячем цикле физики —
     * для обхода есть [forEachParticleAt] / [forEachParticleInCellIndex] / [copyParticlesInto].
     */
    fun getParticles(x: Int, y: Int): IntArray {
        val cellIndex = cellIndexAt(x, y)
        if (cellIndex < 0) return EMPTY_PARTICLES
        return getParticlesIndex(cellIndex)
    }

    /** АЛЛОЦИРУЕТ новый массив. См. комментарий к [getParticles]. */
    fun getParticlesIndex(cellIndex: Int): IntArray {
        val count = particleCounts[cellIndex]
        if (count <= 0) return EMPTY_PARTICLES

        val start = cellIndex * maxAmountOfParticles
        return if (count <= maxAmountOfParticles) {
            grid.copyOfRange(start, start + count)
        } else {
            val extraList = overflowListOrNull(cellIndex) ?: throw Exception("List is null or empty but particleCounts > MAX_AMOUNT_OF_PARTICLES")
            val extraSize = extraList.size
            IntArray(count).apply {
                if (extraSize > 0) System.arraycopy(extraList.elements(), 0, this, 0, extraSize)
                System.arraycopy(grid, start, this, extraSize, maxAmountOfParticles)
            }
        }
    }

    fun clearAll() {
        particleCounts.fill(0)
        mapMoreThenMax.forEach { it.clear() }
    }

    override fun resize() {
        gridWidth = diContext.gridWidth
        gridHeight = diContext.gridHeight
        gridSize = gridWidth * gridHeight
        grid = IntArray(gridSize * maxAmountOfParticles) { -1 }
        particleCounts = IntArray(gridSize)
        halfChunkSize = diContext.chunkSize / 2
        halfChunkShift = shiftOfPowerOfTwo(halfChunkSize)
        mapMoreThenMax = Array(diContext.totalChunks * 2) { Int2ObjectOpenHashMap<IntArrayList>() }
    }

    companion object {
        /**
         * Один общий пустой массив вместо IntArray(0) на каждый промах границы сетки.
         * Иммутабелен по контракту: возвращается только для чтения.
         */
        @JvmField
        val EMPTY_PARTICLES = IntArray(0)
    }

}

/** Номер бита, если value — степень двойки, иначе -1 (тогда деление не заменить сдвигом). */
private fun shiftOfPowerOfTwo(value: Int): Int =
    if (value > 0 && (value and (value - 1)) == 0) Integer.numberOfTrailingZeros(value) else -1


