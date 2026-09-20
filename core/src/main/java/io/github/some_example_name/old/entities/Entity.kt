package io.github.some_example_name.old.entities

import it.unimi.dsi.fastutil.ints.IntArrayList

abstract class Entity(startMaxAmount: Int) {
    protected var maxAmount = startMaxAmount
    var lastId = -1

    var deadStack = IntArrayList(startMaxAmount)

    var isAlive = BooleanArray(maxAmount)
    private var generation = IntArray(maxAmount)
    fun getGeneration(index: Int) = generation[index]
    fun isAliveAndSameGen(index: Int, gen: Int) = isAlive[index] && generation[index] == gen

    var aliveList = IntArrayList(startMaxAmount)
    var positionInAlive = IntArray(maxAmount) { -1 }
    private var cellBoundBeforeClear = 0
    private var oldMaxBeforeResize = 0

    /**
     * Организм, из чьей арены выдан индекс, или -1 если индекс не из арены.
     *
     * Нужен на удалении: слот обязан вернуться в свободный список СВОЕЙ арены, а к моменту
     * delete() определить организм по содержимому сущности уже нельзя. У связи, например,
     * organIndex берётся через её клетку, а клетку к этому моменту могли удалить — она и
     * стала причиной удаления связи, — и её organIndex уже сброшен в -1.
     *
     * Поэтому владелец запоминается в момент выдачи, а не вычисляется в момент возврата.
     */
    private var arenaOwner = IntArray(maxAmount) { -1 }

    /** Организм-владелец слота, или -1 если слот не из арены. */
    fun arenaOwnerOf(index: Int) = arenaOwner[index]

    protected fun add(): Int {
        val cellIndex = if (!deadStack.isEmpty()) {
            deadStack.removeInt(deadStack.size - 1)
        } else {
            ++lastId
        }

        isAlive[cellIndex] = true
        arenaOwner[cellIndex] = -1
        generation[cellIndex]++

        val pos = aliveList.size
        aliveList.add(cellIndex)
        positionInAlive[cellIndex] = pos

        if (maxAmount - 2 < lastId) {
            resize()
        }
        return cellIndex
    }

    /**
     * Занять КОНКРЕТНЫЙ индекс, минуя deadStack и lastId.
     *
     * Так сущности заполняются по аренам: организм получает непрерывный диапазон при
     * рождении и раздаёт из него слоты по мере роста, поэтому все его клетки, частицы и
     * связи лежат в массивах подряд, а не там, куда их положил общий стек переиспользования.
     *
     * [lastId] всё равно ведётся как максимальный использованный индекс — на него опираются
     * clear() и все обходы вида `0..lastId`.
     */
    protected fun addAt(index: Int, arenaOwner: Int = -1): Int {
        ensureCapacity(index)

        if (isAlive[index]) {
            throw IllegalStateException(
                "арена выдала занятый индекс $index — пересечение арен или сбой курсора"
            )
        }

        isAlive[index] = true
        this.arenaOwner[index] = arenaOwner
        generation[index]++

        val pos = aliveList.size
        aliveList.add(index)
        positionInAlive[index] = pos

        if (index > lastId) lastId = index
        return index
    }

    /**
     * Растит массивы так, чтобы [requiredIndex] стал валидным.
     *
     * Аренам это нужно потому, что они выдают индексы ВПЕРЁД: организм резервирует
     * диапазон целиком при рождении, задолго до того, как заполнит его клетками. Обычный
     * рост по lastId такого не покрывает — он реагирует на уже занятый индекс.
     *
     * Цикл, а не одна resize(): шаг роста задан в resize() (5/4), и повторять его до
     * покрытия дешевле, чем дублировать здесь логику пересоздания всех массивов подсущности.
     * Копирование геометрическое, то есть амортизированно линейное, а вызывается это только
     * при рождении организма.
     */
    fun ensureCapacity(requiredIndex: Int) {
        while (requiredIndex > maxAmount - 2) {
            resize()
        }
    }

    /**
     * Забронировать непрерывный диапазон из [count] индексов и вернуть его начало.
     *
     * Ключевое здесь — что бронь СРАЗУ сдвигает [lastId] за конец диапазона. Иначе между
     * резервированием арены и её заполнением обычный [add] выдал бы индекс ВНУТРЬ неё:
     * организм резервирует тысячу слотов при рождении, а занимает их по одному в течение
     * тысяч тиков, и всё это время диапазон для общего аллокатора выглядел бы свободным.
     * Именно так первая версия и падала — субстанция получала слот 0, который арена уже
     * считала своим.
     *
     * Диапазон берётся строго ЗА текущим [lastId], а не с нуля: слоты ниже уже могли быть
     * розданы общим аллокатором (субстанции и террейн появляются раньше первого организма).
     * [deadStack] при этом не трогается — дырки ниже брони остаются доступны обычному
     * add(), и арены с ними не пересекаются, потому что бронь всегда выше.
     */
    fun reserveRange(count: Int): Int {
        val base = lastId + 1
        lastId = base + count - 1
        ensureCapacity(lastId)
        return base
    }

    protected fun delete(index: Int) {
        if (!isAlive[index]) throw IllegalStateException("Entity $index is already dead")

        isAlive[index] = false
        // Слот из арены в общий стек не возвращается — иначе его получит чужой организм.
        // Он вернётся в свободный список СВОЕЙ арены, см. [arenaOwner] и
        // OrganEntity.releaseCellSlot.
        if (arenaOwner[index] == -1) deadStack.add(index)

        val pos = positionInAlive[index]
        if (pos >= 0) {
            val lastPos = aliveList.size - 1
            val lastEntity = aliveList.getInt(lastPos)

            aliveList.set(pos, lastEntity)
            positionInAlive[lastEntity] = pos

            aliveList.removeInt(lastPos)

            positionInAlive[index] = -1
        }
    }

    fun clear() {
        val cellBound = (lastId + 1).coerceAtLeast(0)
        cellBoundBeforeClear = cellBound
        lastId = -1
        deadStack.clear()
        generation.fill(0, 0, cellBound)
        isAlive.fill(false, 0, cellBound)
        arenaOwner.fill(-1, 0, cellBound)

        aliveList.clear()
        positionInAlive.fill(-1, 0, cellBound)

        onClear(cellBound)
    }

    fun resize() {
        val oldMax = maxAmount
        oldMaxBeforeResize = oldMax
        maxAmount = (oldMax * 5 / 4).coerceAtLeast(oldMax + 1)
        run {
            val old = generation
            generation = IntArray(maxAmount)
            System.arraycopy(old, 0, generation, 0, oldMax)
        }
        run {
            val old = isAlive
            isAlive = BooleanArray(maxAmount)
            System.arraycopy(old, 0, isAlive, 0, oldMax)
        }
        run {
            val old = arenaOwner
            arenaOwner = IntArray(maxAmount) { -1 }
            System.arraycopy(old, 0, arenaOwner, 0, oldMax)
        }
        run {
            val old = positionInAlive
            positionInAlive = IntArray(maxAmount) { -1 }
            System.arraycopy(old, 0, positionInAlive, 0, oldMax)
        }

        aliveList.ensureCapacity(maxAmount)

        onResize(oldMax)
    }

    protected abstract fun onCopy()
    protected abstract fun onPaste()
    protected abstract fun onClear(bound: Int)
    protected abstract fun onResize(oldMax: Int)

    protected fun FloatArray.clear(defaultValue: Float = 0f) {
        this.fill(defaultValue, 0, cellBoundBeforeClear)
    }

    protected fun IntArray.clear(defaultValue: Int = 0) {
        this.fill(defaultValue, 0, cellBoundBeforeClear)
    }

    protected fun BooleanArray.clear(defaultValue: Boolean) {
        this.fill(defaultValue, 0, cellBoundBeforeClear)
    }

    protected fun ByteArray.clear(defaultValue: Byte = 0) {
        this.fill(defaultValue, 0, cellBoundBeforeClear)
    }


    protected fun FloatArray.resize(defaultValue: Float = 0f): FloatArray {
        val old = this
        val newArray = if (defaultValue == 0f)
            FloatArray(maxAmount)
        else
            FloatArray(maxAmount) { defaultValue }

        System.arraycopy(old, 0, newArray, 0, oldMaxBeforeResize)
        return newArray
    }

    protected fun IntArray.resize(defaultValue: Int = 0): IntArray {
        val old = this
        val newArray = if (defaultValue == 0)
            IntArray(maxAmount)
        else
            IntArray(maxAmount) { defaultValue }

        System.arraycopy(old, 0, newArray, 0, oldMaxBeforeResize)
        return newArray
    }

    protected fun BooleanArray.resize(defaultValue: Boolean): BooleanArray {
        val old = this
        val newArray = BooleanArray(maxAmount) { defaultValue }

        System.arraycopy(old, 0, newArray, 0, oldMaxBeforeResize)
        return newArray
    }

    protected fun ByteArray.resize(defaultValue: Byte = 0): ByteArray {
        val old = this
        val newArray = if (defaultValue == 0.toByte())
            ByteArray(maxAmount)
        else
            ByteArray(maxAmount) { defaultValue }

        System.arraycopy(old, 0, newArray, 0, oldMaxBeforeResize)
        return newArray
    }
}
