package io.github.some_example_name.old.entities

import com.badlogic.gdx.graphics.Color
import io.github.some_example_name.old.systems.pheromone.PheromonesManager.Companion.MAXIMUM_PHEROMONE_SPREAD_DIAMETER
import io.github.some_example_name.old.systems.pheromone.getSquaredRadius
import io.github.some_example_name.old.systems.physics.GridManager
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet

private const val MAX = 3440

class PheromoneEntity(
    val gridManager: GridManager
): Entity(startMaxAmount = 100) {

    var x = FloatArray(maxAmount)
    var y = FloatArray(maxAmount)
    var time = FloatArray(maxAmount) // время заполнения
    var radiusSquared = FloatArray(maxAmount)
    var emitterIndex = IntArray(maxAmount)
    var color = IntArray(maxAmount)
    var type = IntArray(maxAmount)

    //TODO в идеале заменить на сетку из примитивов для большей скорости
    val pheromoneMapGrid = Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<IntOpenHashSet>>()
    val emitterMap = Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<IntOpenHashSet>>()

    companion object {
        const val MAX_PHEROMONE_TYPES = 32

        private val TYPE_COLORS = IntArray(MAX_PHEROMONE_TYPES) { index ->
            val hue = (index * 360f) / MAX_PHEROMONE_TYPES
            Color().fromHsv(hue, 0.92f, 0.96f).toIntBits()
        }
    }

    fun pack(x: Int, y: Int): Int {
        require(x in 0..MAX && y in 0..MAX)
        return (x shl 12) or y          // 12 бит хватит с запасом (2^12 = 4096 > 3441)
    }

    /** Добавление с учётом type (уникальность теперь считается отдельно для каждого типа) */
    private fun addUnique(pType: Int, key: Int, value: Int): Boolean {
        val typeMap = emitterMap.computeIfAbsent(pType) { Int2ObjectOpenHashMap(4) }
        val set = typeMap.computeIfAbsent(key) { IntOpenHashSet(3) }
        return set.add(value)
    }

    fun addPheromone(x: Float, y: Float, emitterIndex: Int, type: Int, time: Float = 0f): Int? {
        if (emitterIndex != -1) {
            val exactKey = pack(x.toInt(), y.toInt())
            val isFirstAdded = addUnique(type, exactKey, emitterIndex)
            if (!isFirstAdded) return null
        }

        val newIndex = add()

        this.x[newIndex] = x
        this.y[newIndex] = y
        this.time[newIndex] = time
        this.radiusSquared[newIndex] = getSquaredRadius(A = time)
        this.emitterIndex[newIndex] = emitterIndex
        this.type[newIndex] = type

        this.color[newIndex] = if (type in 0 until MAX_PHEROMONE_TYPES) {
            TYPE_COLORS[type]
        } else {
            Color.FIREBRICK.toIntBits()
        }

        val bigGridX = x.toInt() / MAXIMUM_PHEROMONE_SPREAD_DIAMETER
        val bigGridY = y.toInt() / MAXIMUM_PHEROMONE_SPREAD_DIAMETER
        val gridKey = pack(bigGridX, bigGridY)

        // Добавляем в pheromoneMapGrid[type][gridKey]
        val typeGridMap = pheromoneMapGrid.computeIfAbsent(type) { Int2ObjectOpenHashMap(8) }
        typeGridMap.computeIfAbsent(gridKey) { IntOpenHashSet(8) }.add(newIndex)

        return newIndex
    }

    fun deletePheromone(pheromoneIndex: Int, pheromoneGeneration: Int) {
        if (isAlive[pheromoneIndex] && getGeneration(pheromoneIndex) == pheromoneGeneration) {

            val px = x[pheromoneIndex]
            val py = y[pheromoneIndex]
            val emitter = emitterIndex[pheromoneIndex]
            val pType = type[pheromoneIndex]

            val gridX = px.toInt() / MAXIMUM_PHEROMONE_SPREAD_DIAMETER
            val gridY = py.toInt() / MAXIMUM_PHEROMONE_SPREAD_DIAMETER
            val gridKey = pack(gridX, gridY)

            // Удаление из pheromoneMapGrid
            pheromoneMapGrid.get(pType)?.let { typeGridMap ->
                typeGridMap.get(gridKey)?.let { set ->
                    set.remove(pheromoneIndex)
                    if (set.isEmpty()) {
                        typeGridMap.remove(gridKey)
                        if (typeGridMap.isEmpty()) {
                            pheromoneMapGrid.remove(pType)
                        }
                    }
                }
            }

            val exactKey = pack(px.toInt(), py.toInt())

            // Удаление из emitterMap
            emitterMap.get(pType)?.let { typeEmitterMap ->
                typeEmitterMap.get(exactKey)?.let { set ->
                    set.remove(emitter)
                    if (set.isEmpty()) {
                        typeEmitterMap.remove(exactKey)
                        if (typeEmitterMap.isEmpty()) {
                            emitterMap.remove(pType)
                        }
                    }
                }
            }

            delete(pheromoneIndex)

            // Очистка
            x[pheromoneIndex] = 0f
            y[pheromoneIndex] = 0f
            time[pheromoneIndex] = 0f
            radiusSquared[pheromoneIndex] = 0f
            emitterIndex[pheromoneIndex] = -1
            color[pheromoneIndex] = 0
            type[pheromoneIndex] = -1
        }/* else {
        //TODO я не помню зачем я это сделал
            throw Exception("Not deleted")
        }*/
    }

    override fun persistFields(state: EntityState) {
        state.floats(::x)
        state.floats(::y)
        state.floats(::time)
        state.floats(::radiusSquared)
        state.ints(::emitterIndex)
        state.ints(::color)
        state.ints(::type)
    }

    /** Обе карты выводятся из самих феромонов — заполняются тем же путём, что в addPheromone. */
    override fun restoreAfterLoad() {
        pheromoneMapGrid.clear()
        emitterMap.clear()
        for (i in 0 until aliveList.size) {
            val pheromoneIndex = aliveList.getInt(i)
            val px = x[pheromoneIndex]
            val py = y[pheromoneIndex]
            val pType = type[pheromoneIndex]

            val gridKey = pack(
                px.toInt() / MAXIMUM_PHEROMONE_SPREAD_DIAMETER,
                py.toInt() / MAXIMUM_PHEROMONE_SPREAD_DIAMETER
            )
            pheromoneMapGrid.computeIfAbsent(pType) { Int2ObjectOpenHashMap(8) }
                .computeIfAbsent(gridKey) { IntOpenHashSet(8) }
                .add(pheromoneIndex)

            val emitter = emitterIndex[pheromoneIndex]
            if (emitter != -1) addUnique(pType, pack(px.toInt(), py.toInt()), emitter)
        }
    }

    override fun onCopy() {
    }

    override fun onPaste() {
    }

    override fun onClear(bound: Int) {
        x.clear()
        y.clear()
        time.clear()
        radiusSquared.clear()
        emitterIndex.clear(-1)
        color.clear()
        type.clear(-1)                          // ← очищаем новый массив

        pheromoneMapGrid.clear()
        emitterMap.clear()
    }

    override fun onResize(oldMax: Int) {
        x = x.resize()
        y = y.resize()
        time = time.resize()
        radiusSquared = radiusSquared.resize()
        emitterIndex = emitterIndex.resize(-1)
        color = color.resize()
        type = type.resize(-1)                  // ← ресайзим новый массив
    }
}
