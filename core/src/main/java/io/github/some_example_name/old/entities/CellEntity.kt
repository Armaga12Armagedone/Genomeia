package io.github.some_example_name.old.entities

import io.github.some_example_name.old.cells.Cell
import io.github.some_example_name.old.cells.SpecialModData
import io.github.some_example_name.old.core.DISimulationContainer.cellsSettings
import io.github.some_example_name.old.core.SubstrateSettings
import io.github.some_example_name.old.core.utils.OrderedIntPairMap
import io.github.some_example_name.old.systems.genomics.genome.CellAction
import io.github.some_example_name.old.systems.simulation.SimulationData
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList

class CellEntity(
    cellsStartMaxAmount: Int,
    private val particleEntity: ParticleEntity,
    val simulationData: SimulationData,
    val substrateSettings: SubstrateSettings,
    val cellList: List<Cell>,
    private val neuralEntity: NeuralEntity,
    val specialEntity: SpecialEntity
) : Entity(cellsStartMaxAmount) {
    //Particle entity
    var particleIndexes = IntArray(maxAmount) { -1 }
    fun getParticleIndex(index: Int) = particleIndexes[index]
    fun getX(index: Int) = particleEntity.x[particleIndexes[index]]
    fun getY(index: Int) = particleEntity.y[particleIndexes[index]]
    fun setX(index: Int, value: Float) { particleEntity.x[particleIndexes[index]] = value }
    fun setY(index: Int, value: Float) { particleEntity.y[particleIndexes[index]] = value }
    fun getVx(index: Int) = particleEntity.vx[particleIndexes[index]]
    fun getVy(index: Int) = particleEntity.vy[particleIndexes[index]]
    fun setVx(index: Int, value: Float) { particleEntity.vx[particleIndexes[index]] = value }
    fun setVy(index: Int, value: Float) { particleEntity.vy[particleIndexes[index]] = value }
    fun getDragCoefficient(index: Int) = particleEntity.dragCoefficient[particleIndexes[index]]
    fun setDragCoefficient(index: Int, value: Float) { particleEntity.dragCoefficient[particleIndexes[index]] = value }
    fun getEffectOnContact(index: Int) = particleEntity.effectOnContact[particleIndexes[index]]
    fun setEffectOnContact(index: Int, value: Boolean) { particleEntity.effectOnContact[particleIndexes[index]] = value }
    fun getIsCollidable(index: Int) = particleEntity.isCollidable[particleIndexes[index]]
    fun setIsCollidable(index: Int, value: Boolean) { particleEntity.isCollidable[particleIndexes[index]] = value }
    fun getCellStiffness(index: Int) = particleEntity.cellStiffness[particleIndexes[index]]
    fun setCellStiffness(index: Int, value: Float) { particleEntity.cellStiffness[particleIndexes[index]] = value }
    fun getRadius(index: Int) = particleEntity.radius[particleIndexes[index]]
    fun setRadius(index: Int, value: Float) { particleEntity.radius[particleIndexes[index]] = value }
    fun getGridId(index: Int) = particleEntity.gridId[particleIndexes[index]]
    fun seGridId(index: Int, value: Int) { particleEntity.gridId[particleIndexes[index]] = value }
    fun getSimTime(index: Int) = simulationData.timeSimulation
    fun getColor(index: Int) = particleEntity.color[particleIndexes[index]]
    fun setColor(index: Int, value: Int) { particleEntity.color[particleIndexes[index]] = value }
    fun getIsPheromoneEmitter(index: Int) = particleEntity.isPheromoneEmitter[particleIndexes[index]]
    fun setIsPheromoneEmitter(index: Int, value: Boolean) { particleEntity.isPheromoneEmitter[particleIndexes[index]] = value }
    var cellGenomeId = IntArray(maxAmount) { -1 }
    var cellActions: Array<CellAction?> = arrayOfNulls(maxAmount)
    var organIndex = IntArray(maxAmount) { -1 }
    var parentIndex = IntArray(maxAmount) { -1 }
    var angleCos = FloatArray(maxAmount)
    var angleSin = FloatArray(maxAmount)
    var angleDirectedCos = FloatArray(maxAmount)
    var angleDirectedSin = FloatArray(maxAmount)
    var angleCompensationCos = FloatArray(maxAmount)
    var angleCompensationSin = FloatArray(maxAmount)
    var energyNecessaryToDivide = FloatArray(maxAmount) { 2f }
    var energyNecessaryToMutate = FloatArray(maxAmount) { 1f }
    var isDividedInThisStage = BooleanArray(maxAmount)
    var isMutateInThisStage = BooleanArray(maxAmount)
    var cellType = ByteArray(maxAmount)
    var energy = FloatArray(maxAmount)
    var maxEnergy = FloatArray(maxAmount)
    var isNeural = BooleanArray(maxAmount)
    var neuronImpulseInput = FloatArray(maxAmount)
    var neuronImpulseOutput = FloatArray(maxAmount)
    var isOnEdge = BooleanArray(maxAmount)
    var degreeOfShortening = FloatArray(maxAmount) { 1f }
    var pheromoneType = IntArray(maxAmount) { -1 }
    var linkAmount = IntArray(maxAmount) { 0 }
    var command = ByteArray(maxAmount) { -1 }
    var neuralConnections = Int2ObjectOpenHashMap<IntArrayList>()
    @Transient val organToIdToIndex = OrderedIntPairMap(maxAmount)

    /**
     * Инлайн-список соседей по физическим связям: MAX_LINKS_PER_CELL слотов на клетку,
     * подряд, начиная с cellIndex * MAX_LINKS_PER_CELL. Пустой слот = -1.
     *
     * Зачем это вообще нужно: проверка «связаны ли две клетки» делается в repulse на
     * каждое касание клетка-клетка, то есть миллионы раз за тик. Раньше это был
     * UnorderedIntPairMap поверх Long2IntOpenHashMap на 100k записей — таблица
     * ~1.5+ МБ, то есть больше любого L2, а обращение к ней это случайный пробинг:
     * почти гарантированный промах в L3/RAM (~200 циклов), плюс возможное линейное
     * пробирование по нескольким кэш-линиям. Одна такая проверка стоила дороже, чем
     * вся остальная математика касания.
     *
     * Слотов 16, но горячими являются только первые HOT_LINKS = 8: 8 int'ов это 32 байта,
     * и такой блок почти всегда попадает в одну кэш-линию целиком. Полные 64 байта дали бы
     * ровно размер линии, но не её выравнивание: данные массива в JVM начинаются после
     * хедера объекта, сам объект выровнен по 8/16 байт, а не по 64 — то есть 64-байтный
     * блок почти гарантированно лёг бы на границу и стоил двух промахов вместо одного.
     * Поэтому вторая половина списка читается только у клеток, у которых связей реально
     * больше восьми (см. areCellsLinked).
     *
     * Отсюда и ограничение симуляции: у клетки не может быть больше MAX_LINKS_PER_CELL
     * физических связей. Список хранится плотно (все живые соседи идут подряд от начала),
     * удаление делается swap-with-last — на плотность опирается и быстрый выход из скана,
     * и canAddCellLink.
     *
     * Важно: linkAmount считает и физические, и нейронные связи (это игровой сенсор),
     * поэтому он НЕ является длиной этого списка и не может использоваться как счётчик.
     */
    var cellLinks = IntArray(maxAmount * MAX_LINKS_PER_CELL) { -1 }

    /**
     * Горячая проверка из repulse.
     *
     * Первые HOT_LINKS слотов сканируются развёрнуто и без ранних выходов: они лежат в
     * одной кэш-линии, поэтому лишние загрузки бесплатны, а вот выход по первому -1
     * добавил бы плохо предсказуемую ветку (у клеток бывает и 2, и 6 связей) — промах
     * предсказателя дороже семи чтений из уже загруженной линии. Результаты сравнений
     * складываются через `or` без короткого замыкания, чтобы не появилось восемь
     * условных переходов.
     *
     * Дальше стоит ровно одна ветка: список плотный, поэтому непустой слот HOT_LINKS - 1
     * означает «связей девять или больше». У 99% клеток их 2-6, так что ветка
     * предсказывается почти идеально, и вторая кэш-линия в типичном случае не трогается
     * вообще — проверка стоит столько же, сколько при лимите в 8 связей.
     *
     * otherCellIndex всегда валидный индекс живой клетки (никогда -1), так что пустые
     * слоты со -1 никогда не дадут ложного совпадения.
     */
    fun areCellsLinked(cellIndex: Int, otherCellIndex: Int): Boolean {
        val links = cellLinks
        val base = cellIndex shl LINKS_SHIFT

        val foundInHotHalf = (links[base] == otherCellIndex) or
            (links[base + 1] == otherCellIndex) or
            (links[base + 2] == otherCellIndex) or
            (links[base + 3] == otherCellIndex) or
            (links[base + 4] == otherCellIndex) or
            (links[base + 5] == otherCellIndex) or
            (links[base + 6] == otherCellIndex) or
            (links[base + 7] == otherCellIndex)

        if (foundInHotHalf) return true

        // Список плотный: пустой последний слот первой половины значит, что связей меньше
        // девяти и вторая половина заведомо пустая. Не читаем её — экономим кэш-линию.
        if (links[base + HOT_LINKS - 1] == -1) return false

        return (links[base + 8] == otherCellIndex) or
            (links[base + 9] == otherCellIndex) or
            (links[base + 10] == otherCellIndex) or
            (links[base + 11] == otherCellIndex) or
            (links[base + 12] == otherCellIndex) or
            (links[base + 13] == otherCellIndex) or
            (links[base + 14] == otherCellIndex) or
            (links[base + 15] == otherCellIndex)
    }


    /** Есть ли у клетки свободный слот под ещё одну физическую связь. */
    fun canAddCellLink(cellIndex: Int) =
        cellLinks[(cellIndex shl LINKS_SHIFT) + MAX_LINKS_PER_CELL - 1] == -1

    /**
     * Добавляет соседа в первый свободный слот. false — слотов больше нет.
     * Вызывается только из однопоточной фазы применения команд.
     */
    fun addCellLink(cellIndex: Int, otherCellIndex: Int): Boolean {
        val links = cellLinks
        val base = cellIndex shl LINKS_SHIFT
        for (i in base until base + MAX_LINKS_PER_CELL) {
            if (links[i] == -1) {
                links[i] = otherCellIndex
                return true
            }
        }
        return false
    }

    /**
     * Удаляет одно вхождение соседа, сохраняя плотность списка (swap with last).
     * Если соседа нет — no-op (связь могла быть уже снята вместе со смертью клетки).
     */
    fun removeCellLink(cellIndex: Int, otherCellIndex: Int) {
        val links = cellLinks
        val base = cellIndex shl LINKS_SHIFT

        var slot = -1
        var last = -1
        for (i in base until base + MAX_LINKS_PER_CELL) {
            val neighbour = links[i]
            if (neighbour == -1) break
            if (neighbour == otherCellIndex && slot == -1) slot = i
            last = i
        }

        if (slot == -1) return

        links[slot] = links[last]
        links[last] = -1
    }

    fun clearCellLinks(cellIndex: Int) {
        val base = cellIndex shl LINKS_SHIFT
        cellLinks.fill(-1, base, base + MAX_LINKS_PER_CELL)
    }


    fun addNeuralConnection(cellIndex: Int, targetNeuralIndex: Int) {
        val list = neuralConnections[cellIndex] ?: IntArrayList(2).also {
            neuralConnections[cellIndex] = it
        }

        if (!list.contains(targetNeuralIndex)) {
            list.add(targetNeuralIndex)
        }
    }

    fun removeNeuralConnection(cellIndex: Int, targetNeuralIndex: Int) {
        val list = neuralConnections.get(cellIndex) ?: return
        list.rem(targetNeuralIndex)
    }

    //Neural entity
    var neuralIndexes = IntArray(maxAmount) { -1 }
    fun getNeuralGeneration(index: Int) = neuralEntity.getGeneration(neuralIndexes[index])
    fun getIsNeuronTransportable(index: Int) = neuralEntity.isNeuronTransportable[neuralIndexes[index]]
    fun setIsNeuronTransportable(index: Int, value: Boolean) { neuralEntity.isNeuronTransportable[neuralIndexes[index]] = value }
    fun getActivationFuncType(index: Int) = neuralEntity.activationFuncType[neuralIndexes[index]].toInt()
    fun setActivationFuncType(index: Int, value: Byte) { neuralEntity.activationFuncType[neuralIndexes[index]] = value }
    fun getA(index: Int) = neuralEntity.a[neuralIndexes[index]]
    fun setA(index: Int, value: Float) { neuralEntity.a[neuralIndexes[index]] = value }
    fun getB(index: Int) = neuralEntity.b[neuralIndexes[index]]
    fun setB(index: Int, value: Float) { neuralEntity.b[neuralIndexes[index]] = value }
    fun getC(index: Int) = neuralEntity.c[neuralIndexes[index]]
    fun setC(index: Int, value: Float) { neuralEntity.c[neuralIndexes[index]] = value }
    fun getDTime(index: Int) = neuralEntity.dTime[neuralIndexes[index]]
    fun setDTime(index: Int, value: Float) { neuralEntity.dTime[neuralIndexes[index]] = value}
    fun getRemember(index: Int) = neuralEntity.remember[neuralIndexes[index]]
    fun setRemember(index: Int, value: Float) { neuralEntity.remember[neuralIndexes[index]] = value }
    fun getIsSum(index: Int) = neuralEntity.isSum[neuralIndexes[index]]
    fun setIsSum(index: Int, value: Boolean) {neuralEntity.isSum.set(neuralIndexes[index], value)}
    fun getTickRed(index: Int) = neuralEntity.tickRed[neuralIndexes[index]]
    fun setTickRed(index: Int, value: Int) { neuralEntity.tickRed[neuralIndexes[index]] = value }
    fun getTickPain(index: Int) = neuralEntity.tickPain[neuralIndexes[index]]
    fun setTickPain(index: Int, value: Int) { neuralEntity.tickPain[neuralIndexes[index]] = value }
    fun getWeight(index: Int) = neuralEntity.weight[neuralIndexes[index]]
    fun setWeight(index: Int, value: Float) { neuralEntity.weight[neuralIndexes[index]] = value}

    fun deleteNeural(cellIndex: Int, neuralGeneration: Int? = null) {
        val neuralIndex = neuralIndexes[cellIndex]
        if (neuralIndex == -1) return

        neuronImpulseInput[cellIndex] = 0f
        neuronImpulseOutput[cellIndex] = 0f
        isNeural[cellIndex] = false

        if (neuralEntity.isAlive[neuralIndex] && (neuralGeneration == null
                || neuralEntity.getGeneration(neuralIndex) == neuralGeneration)) {
            neuralEntity.deleteNeural(neuralIndex)
            neuralIndexes[cellIndex] = -1
        }
    }

    fun addNeural(
        index: Int,
        cellType: Int,
        a: Float = 1f,
        b: Float = 0f,
        c: Float = 0f,
        isSum: Boolean = true,
        activationFuncType: Byte = 0
    ) {
        neuronImpulseInput[index] = 0f
        neuronImpulseOutput[index] = 0f
        isNeural[index] = true
        neuralIndexes[index] = neuralEntity.addNeural(cellType, a, b, c, isSum, activationFuncType)
    }

    fun addCell(
        x: Float,
        y: Float,
        color: Int,
        radius: Float = 0.5f,
        cellGenomeId: Int = 0,
        cellType: Int,
        organIndex: Int,
        parentIndex: Int = -1,
        angleCos: Float = 1f,
        angleSin: Float = 0f,
        angleDiffCos: Float = 1f,
        angleDiffSin: Float = 0f,
        colorDifferentiation: Int = 7,
        visibilityRange: Float = 4.25f,
        a: Float = 1f,
        b: Float = 0f,
        c: Float = 0f,
        isSum: Boolean = true,
        activationFuncType: Byte = 7,
        speed: Float = 0f,
        pheromoneType: Int = -1,
        specialModData: SpecialModData? = null
    ): Int {
        val cellIndex = add()

        particleIndexes[cellIndex] = particleEntity.addParticle(
            x = x,
            y = y,
            color = color,
            radius = radius,
            dragCoefficient = substrateSettings.data.viscosityOfTheEnvironment,
            effectOnContact = cellList[cellType].effectOnContact,
            isCollidable = cellList[cellType].isCollidable,
            cellStiffness = cellsSettings[cellType].cellStiffness,
            isCell = true,
            isSub = false,
            holderEntityIndex = cellIndex
        )
        this.cellGenomeId[cellIndex] = cellGenomeId
        cellActions[cellIndex] = null
        this.organIndex[cellIndex] = organIndex
        this.parentIndex[cellIndex] = parentIndex
        this.angleCos[cellIndex] = angleCos * angleDiffCos - angleSin * angleDiffSin
        this.angleSin[cellIndex] = angleSin * angleDiffCos + angleCos * angleDiffSin
        this.angleDirectedCos[cellIndex] = angleDiffCos
        this.angleDirectedSin[cellIndex] = angleDiffSin
        this.angleCompensationCos[cellIndex] = 1f
        this.angleCompensationSin[cellIndex] = 0f
        energyNecessaryToDivide[cellIndex] = 2f
        energyNecessaryToMutate[cellIndex] = 1f
        isDividedInThisStage[cellIndex] = false
        isMutateInThisStage[cellIndex] = false
        this.cellType[cellIndex] = cellType.toByte()
        energy[cellIndex] = 0.1f
        maxEnergy[cellIndex] = cellsSettings[cellType].maxEnergy
        isOnEdge[cellIndex] = true
        this.degreeOfShortening[cellIndex] = 1f
        this.pheromoneType[cellIndex] = pheromoneType
        linkAmount[cellIndex] = 0
        // Индексы переиспользуются через deadStack, поэтому слоты связей нужно
        // обязательно вычистить: иначе новая клетка унаследует соседей мёртвой.
        clearCellLinks(cellIndex)
        command[cellIndex] = -1
        val cell = cellList[cellType]

        if (cell.doesNeedNeuralConnections) {
            neuralConnections.put(cellIndex, IntArrayList(2))
        }

        if (cell.isNeural) {
            addNeural(cellIndex, cellType, a, b, c, isSum, activationFuncType)
        } else {
            neuronImpulseInput[cellIndex] = 0f
            neuronImpulseOutput[cellIndex] = 0f
            isNeural[cellIndex] = false
            neuralIndexes[cellIndex] = -1
        }

        specialEntity.addSpecial(
            cell = cell,
            colorDifferentiation = colorDifferentiation,
            visibilityRange = visibilityRange,
            speed = speed,
            specialModData = specialModData
        )

        organToIdToIndex.put(organIndex, cellGenomeId, cellIndex)

        return cellIndex
    }

    fun deleteCell(cellIndex: Int) {
        delete(cellIndex)

        organToIdToIndex.remove(organIndex[cellIndex], cellGenomeId[cellIndex])
        particleEntity.deleteParticle(particleIndexes[cellIndex])
        particleIndexes[cellIndex] = -1

        cellGenomeId[cellIndex] = -1
        cellActions[cellIndex] = null
        organIndex[cellIndex] = -1
        parentIndex[cellIndex] = -1
        this.angleCos[cellIndex] = 1f
        this.angleSin[cellIndex] = 0f
        this.angleDirectedCos[cellIndex] = 1f
        this.angleDirectedSin[cellIndex] = 0f
        this.angleCompensationCos[cellIndex] = 1f
        this.angleCompensationSin[cellIndex] = 0f
        energyNecessaryToDivide[cellIndex] = 2f
        energyNecessaryToMutate[cellIndex] = 1f
        isDividedInThisStage[cellIndex] = true
        isMutateInThisStage[cellIndex] = true
        val cellType = cellType[cellIndex]
        val cell = cellList[cellType.toInt()]
        this.cellType[cellIndex] = 0
        energy[cellIndex] = 0f
        maxEnergy[cellIndex] = 0f
        isNeural[cellIndex] = false
        neuronImpulseInput[cellIndex] = 0f
        neuronImpulseOutput[cellIndex] = 0f
        isOnEdge[cellIndex] = true
        this.degreeOfShortening[cellIndex] = 1f
        pheromoneType[cellIndex] = -1
        linkAmount[cellIndex] = 0
        // Сами связи ещё живы (их снимет LinkPhysicsSystem, увидев мёртвую клетку и
        // отправив DELETE_LINK), но список этой клетки уже не нужен, а слот индекса
        // может быть переиспользован. Обратные ссылки у соседей уберёт deleteLink.
        clearCellLinks(cellIndex)
        command[cellIndex] = -1
        neuralConnections.remove(cellIndex)


        deleteNeural(cellIndex = cellIndex)

        specialEntity.delete(cell = cell, cellIndex = cellIndex)
    }

    override fun onCopy() {

    }

    override fun onPaste() {

        //TODO map_save востанвить по данным - organToIdToIndex
    }

    override fun onClear(bound: Int) {
        particleIndexes.clear(-1)
        cellGenomeId.clear(-1)
        cellActions.fill(null, 0, bound)
        organIndex.clear(-1)
        parentIndex.clear(-1)
        angleCos.clear(1f)
        angleSin.clear()
        angleDirectedCos.clear(1f)
        angleDirectedSin.clear()
        angleCompensationCos.clear(1f)
        angleCompensationSin.clear()
        energyNecessaryToDivide.clear(2f)
        energyNecessaryToMutate.clear(1f)
        isDividedInThisStage.clear(false)
        isMutateInThisStage.clear(false)
        cellType.clear()
        energy.clear()
        maxEnergy.clear()
        isNeural.clear(false)
        neuronImpulseInput.clear()
        neuronImpulseOutput.clear()
        neuralIndexes.clear()
        isOnEdge.clear(true)
        degreeOfShortening.clear(1f)
        pheromoneType.clear(-1)
        linkAmount.clear(0)
        cellLinks.fill(-1, 0, bound shl LINKS_SHIFT)
        command.clear(-1)
        neuralConnections.clear()
        organToIdToIndex.clear()
    }


    override fun onResize(oldMax: Int) {
        particleIndexes = particleIndexes.resize(-1)
        cellGenomeId = cellGenomeId.resize(-1)
        run {
            val old = cellActions
            cellActions = arrayOfNulls(maxAmount)
            System.arraycopy(old, 0, cellActions, 0, oldMax)
        }
        organIndex = organIndex.resize(-1)
        parentIndex = parentIndex.resize(-1)
        angleCos = angleCos.resize(1f)
        angleSin = angleSin.resize()
        angleDirectedCos = angleDirectedCos.resize(1f)
        angleDirectedSin = angleDirectedSin.resize()
        angleCompensationCos = angleCompensationCos.resize(1f)
        angleCompensationSin = angleCompensationSin.resize()
        energyNecessaryToDivide = energyNecessaryToDivide.resize(2f)
        energyNecessaryToMutate = energyNecessaryToMutate.resize(1f)
        isDividedInThisStage = isDividedInThisStage.resize(false)
        isMutateInThisStage = isMutateInThisStage.resize(false)
        cellType = cellType.resize()
        energy = energy.resize()
        maxEnergy = maxEnergy.resize()
        isNeural = isNeural.resize(false)
        neuronImpulseInput = neuronImpulseInput.resize()
        neuronImpulseOutput = neuronImpulseOutput.resize()
        neuralIndexes = neuralIndexes.resize()
        isOnEdge = isOnEdge.resize(true)
        degreeOfShortening = degreeOfShortening.resize(1f)
        pheromoneType = pheromoneType.resize(-1)
        linkAmount = linkAmount.resize(0)
        // Массив связей растёт не как остальные: на клетку в нём MAX_LINKS_PER_CELL
        // слотов, поэтому и размер, и копируемый диапазон умножаются на них же.
        run {
            val old = cellLinks
            cellLinks = IntArray(maxAmount shl LINKS_SHIFT) { -1 }
            System.arraycopy(old, 0, cellLinks, 0, oldMax shl LINKS_SHIFT)
        }
        command = command.resize(-1)
    }

    companion object {
        /**
         * Ограничение симуляции: столько физических связей максимум может быть у клетки.
         * Менять только на степень двойки и синхронно с LINKS_SHIFT (плюс развёрнутый
         * скан в areCellsLinked).
         */
        const val MAX_LINKS_PER_CELL = 16

        /**
         * Сколько первых слотов считаются горячими: 8 int'ов = 32 байта, такой блок почти
         * всегда целиком лежит в одной кэш-линии. Остальные слоты — «холодный хвост» для
         * редких клеток с более чем восемью связями.
         */
        const val HOT_LINKS = 8

        /** log2(MAX_LINKS_PER_CELL): умножение на индекс базы делается сдвигом. */
        const val LINKS_SHIFT = 4
    }

}

