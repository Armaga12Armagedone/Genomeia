package io.github.some_example_name.old.entities


class OrganEntity(
    organStartMaxAmount: Int,
    /**
     * Выдавать ли организмам арены.
     *
     * Выключено в редакторе генома: там живёт один организм в сотне слотов, а арена
     * рассчитана на взрослое тело (см. [DEFAULT_MAX_CELLS]) и растянула бы все массивы
     * редактора на порядок без всякой пользы — параллельных фаз там нет.
     *
     * При false [allocateArenas] ничего не делает, [hasArena] всегда false, и все пути
     * создания сущностей уходят в общий аллокатор ровно как до появления арен.
     */
    private val arenasEnabled: Boolean = true
): Entity(organStartMaxAmount) {

    var genomeIndex = IntArray(maxAmount) { -1 }
    var genomeSize = IntArray(maxAmount)
    var stage = IntArray(maxAmount)
    var dividedTimes = IntArray(maxAmount)
    var mutatedTimes = IntArray(maxAmount)
    var alreadyGrownUp = BooleanArray(maxAmount)
    var divideCounterThisStage = IntArray(maxAmount)
    var mutateCounterThisStage = IntArray(maxAmount)
    var divideAmountThisStage = IntArray(maxAmount)
    var mutateAmountThisStage = IntArray(maxAmount)
    var justChangedStage = BooleanArray(maxAmount)

    // ===================================================================================
    // АРЕНЫ ОРГАНИЗМА
    //
    // Организм при рождении резервирует непрерывный диапазон индексов в CellEntity,
    // ParticleEntity, LinkEntity и NeuralLinkEntity и дальше по мере роста раздаёт слоты
    // только из него. Смысл — в том, чтобы все клетки, частицы и связи одного тела лежали
    // в массивах ПОДРЯД.
    //
    // Зачем: общий аллокатор (Entity.add) сначала опустошает deadStack, а тот в
    // установившемся режиме (рождений примерно столько же, сколько смертей) не пуст почти
    // никогда. Поэтому индексы растущего организма — это переиспользованные слоты умерших
    // клеток со всего мира, разбросанные по всему адресному пространству. Фаза связей на
    // каждую связь делает два случайных прыжка (связь -> клетки -> частицы), и попадания
    // в кэш зависят от истории мира, а не от геометрии тела.
    //
    // С аренами тот же обход становится потоковым: связи организма идут подряд, а клетки
    // и частицы, на которые они ссылаются, лежат внутри одного узкого окна.
    //
    // ПАРАЛЛЕЛЬНЫЕ АРЕНЫ КЛЕТОК И ЧАСТИЦ
    // ----------------------------------
    // Арены клеток и частиц выдаются ОДНОГО размера и заполняются синхронно, поэтому
    // клетка со смещением k владеет частицей с тем же смещением k:
    //
    //     particleIndex = particleArenaBase + (cellIndex - cellArenaBase)
    //
    // Это самый горячий переход всей фазы связей, и здесь он становится арифметикой
    // вместо чтения из cellEntity.particleIndexes. Сам particleIndexes остаётся —
    // он нужен субстанциям (у них организма нет) и служит проверкой под DEBUG_CHECKS.
    //
    // ЧЕГО ЗДЕСЬ ПОКА НЕТ
    // -------------------
    // Переиспользования. Умерший слот внутри арены не возвращается никуда (см.
    // Entity.arenaAllocated), а арена умершего организма не отдаётся новым — курсоры
    // только растут. Это осознанное упрощение первой итерации: цель — измерить эффект от
    // непрерывной раскладки, не втягивая в это аллокатор со свободными списками.
    // Плата за смерти клеток внутри живого организма — запас ёмкости [ARENA_HEADROOM].
    // ===================================================================================

    /** Начало диапазона организма в CellEntity, -1 если арены нет. */
    var cellArenaBase = IntArray(maxAmount) { -1 }

    /** Сколько слотов зарезервировано под клетки (и, столько же, под частицы). */
    var cellArenaCapacity = IntArray(maxAmount)

    /** Сколько слотов клеток уже роздано: bump-указатель внутри арены. */
    var cellArenaUsed = IntArray(maxAmount)

    /** Начало диапазона организма в ParticleEntity. Ёмкость общая с клетками. */
    var particleArenaBase = IntArray(maxAmount) { -1 }

    var linkArenaBase = IntArray(maxAmount) { -1 }
    var linkArenaCapacity = IntArray(maxAmount)
    var linkArenaUsed = IntArray(maxAmount)

    var neuralLinkArenaBase = IntArray(maxAmount) { -1 }
    var neuralLinkArenaCapacity = IntArray(maxAmount)
    var neuralLinkArenaUsed = IntArray(maxAmount)

    /**
     * Сущности, у которых бронируются диапазоны. Связываются один раз из DI-контейнера
     * через [bindEntities] — конструктором нельзя, потому что OrganEntity создаётся
     * раньше всех остальных (и CellEntity уже зависит от него).
     *
     * Пока не связаны, арены не выдаются: [allocateArenas] выходит сразу. Так редактор и
     * любой другой контейнер, забывший про связывание, продолжают работать прежним путём,
     * а не падают.
     */
    private var cellEntityRef: Entity? = null
    private var particleEntityRef: Entity? = null
    private var linkEntityRef: Entity? = null
    private var neuralLinkEntityRef: Entity? = null

    fun bindEntities(
        cellEntity: Entity,
        particleEntity: Entity,
        linkEntity: Entity,
        neuralLinkEntity: Entity
    ) {
        cellEntityRef = cellEntity
        particleEntityRef = particleEntity
        linkEntityRef = linkEntity
        neuralLinkEntityRef = neuralLinkEntity
    }

    /** Есть ли у организма арена. Клетки без организма (organIndex == -1) идут общим путём. */
    fun hasArena(organIndex: Int) = organIndex != -1 && cellArenaBase[organIndex] != -1

    /**
     * Резервирует диапазоны под организм. Вызывать сразу после [addOrgan], до создания
     * первой клетки.
     *
     * Ёмкости берутся с запасом [ARENA_HEADROOM]: клетки внутри живого организма умирают
     * (отрывается кусок тела, срабатывает отладочный стресс-тест), а освободившийся слот
     * в этой итерации не переиспользуется. Запас — это плата за отсутствие свободного
     * списка, и он же определяет, сколько смертей организм переживёт, не упёршись в
     * границу арены.
     */
    fun allocateArenas(
        organIndex: Int,
        maxCells: Int = DEFAULT_MAX_CELLS,
        maxLinks: Int = DEFAULT_MAX_LINKS,
        maxNeuralLinks: Int = DEFAULT_MAX_NEURAL_LINKS
    ) {
        if (!arenasEnabled) return

        val cells = cellEntityRef ?: return
        val particles = particleEntityRef ?: return
        val links = linkEntityRef ?: return
        val neuralLinks = neuralLinkEntityRef ?: return

        val cellCapacity = withHeadroom(maxCells)
        val linkCapacity = withHeadroom(maxLinks)
        val neuralCapacity = withHeadroom(maxNeuralLinks)

        // Бронь делает сама сущность: она знает свой lastId, то есть где кончается уже
        // розданное. Свои курсоры здесь вести нельзя — субстанции и террейн создаются
        // раньше первого организма и занимают индексы с нуля.
        //
        // Зазор в конце — против false sharing на границе арен. Округления ёмкости до
        // кратного 64 недостаточно: данные массива в JVM начинаются после заголовка
        // объекта, а сам объект выровнен по 8/16 байт, не по 64, поэтому граница арены
        // попадает в середину кэш-линии со смещением, которое из Kotlin не контролируется.
        // Нужен именно реальный пропуск элементов, и 64 покрывают линию даже у
        // ByteArray/BooleanArray, где элемент — байт.
        cellArenaBase[organIndex] = cells.reserveRange(cellCapacity + ARENA_GAP)
        cellArenaCapacity[organIndex] = cellCapacity
        cellArenaUsed[organIndex] = 0

        particleArenaBase[organIndex] = particles.reserveRange(cellCapacity + ARENA_GAP)

        linkArenaBase[organIndex] = links.reserveRange(linkCapacity + ARENA_GAP)
        linkArenaCapacity[organIndex] = linkCapacity
        linkArenaUsed[organIndex] = 0

        neuralLinkArenaBase[organIndex] = neuralLinks.reserveRange(neuralCapacity + ARENA_GAP)
        neuralLinkArenaCapacity[organIndex] = neuralCapacity
        neuralLinkArenaUsed[organIndex] = 0
    }

    /**
     * Следующий свободный индекс клетки внутри арены организма, или -1 если арена исчерпана.
     *
     * -1 означает, что организм вырос больше, чем под него зарезервировали. Вызывающий
     * обязан это проверить: молча уйти в общий аллокатор нельзя, иначе часть тела окажется
     * вне арены, и обход по диапазону её просто не увидит.
     */
    fun takeCellSlot(organIndex: Int): Int {
        val used = cellArenaUsed[organIndex]
        if (used >= cellArenaCapacity[organIndex]) return -1
        cellArenaUsed[organIndex] = used + 1
        return cellArenaBase[organIndex] + used
    }

    fun takeLinkSlot(organIndex: Int): Int {
        val used = linkArenaUsed[organIndex]
        if (used >= linkArenaCapacity[organIndex]) return -1
        linkArenaUsed[organIndex] = used + 1
        return linkArenaBase[organIndex] + used
    }

    fun takeNeuralLinkSlot(organIndex: Int): Int {
        val used = neuralLinkArenaUsed[organIndex]
        if (used >= neuralLinkArenaCapacity[organIndex]) return -1
        neuralLinkArenaUsed[organIndex] = used + 1
        return neuralLinkArenaBase[organIndex] + used
    }

    /**
     * Частица клетки по параллельности арен, без чтения cellEntity.particleIndexes.
     * Вызывать только когда [hasArena] истинно.
     */
    fun particleIndexOfCell(organIndex: Int, cellIndex: Int) =
        particleArenaBase[organIndex] + (cellIndex - cellArenaBase[organIndex])

    /** Верхняя граница занятой части арены клеток — предел обхода организма. */
    fun cellArenaEnd(organIndex: Int) = cellArenaBase[organIndex] + cellArenaUsed[organIndex]

    fun linkArenaEnd(organIndex: Int) = linkArenaBase[organIndex] + linkArenaUsed[organIndex]

    fun neuralLinkArenaEnd(organIndex: Int) =
        neuralLinkArenaBase[organIndex] + neuralLinkArenaUsed[organIndex]

    private fun withHeadroom(size: Int) = size + (size * ARENA_HEADROOM_PERCENT / 100)

    fun addOrgan(
        genomeIndex: Int,
        genomeSize: Int,
        dividedTimes: Int = 0,
        mutatedTimes: Int = 0,
    ): Int {
        val organIndex = add()

        this.genomeIndex[organIndex] = genomeIndex
        this.genomeSize[organIndex] = genomeSize
        this.stage[organIndex] = 0
        this.dividedTimes[organIndex] = dividedTimes
        this.mutatedTimes[organIndex] = mutatedTimes
        this.alreadyGrownUp[organIndex] = false
        this.divideCounterThisStage[organIndex] = 0
        this.mutateCounterThisStage[organIndex] = 0
        this.divideAmountThisStage[organIndex] = dividedTimes
        this.mutateAmountThisStage[organIndex] = mutatedTimes
        this.justChangedStage[organIndex] = true
        return organIndex
    }

    fun deleteOrgan(organIndex: Int) {
        delete(organIndex)

        genomeIndex[organIndex] = -1
        genomeSize[organIndex] = 0
        stage[organIndex] = 0
        dividedTimes[organIndex] = 0
        mutatedTimes[organIndex] = 0
        alreadyGrownUp[organIndex] = false
        divideCounterThisStage[organIndex] = 0
        mutateCounterThisStage[organIndex] = 0
        divideAmountThisStage[organIndex] = 0
        mutateAmountThisStage[organIndex] = 0
        justChangedStage[organIndex] = true

        // Арена НЕ возвращается в оборот: курсоры только растут. Дескриптор гасится, чтобы
        // мёртвый organIndex не выглядел как владелец живого диапазона — иначе обход по
        // аренам прошёлся бы по чужой памяти, если индекс организма переиспользуется.
        cellArenaBase[organIndex] = -1
        cellArenaCapacity[organIndex] = 0
        cellArenaUsed[organIndex] = 0
        particleArenaBase[organIndex] = -1
        linkArenaBase[organIndex] = -1
        linkArenaCapacity[organIndex] = 0
        linkArenaUsed[organIndex] = 0
        neuralLinkArenaBase[organIndex] = -1
        neuralLinkArenaCapacity[organIndex] = 0
        neuralLinkArenaUsed[organIndex] = 0
    }

    override fun onCopy() {

    }

    override fun onPaste() {

    }

    override fun onClear(bound: Int) {
        genomeIndex.clear(-1)
        genomeSize.clear()
        stage.clear()
        dividedTimes.clear()
        mutatedTimes.clear()
        alreadyGrownUp.clear(false)
        divideCounterThisStage.clear()
        mutateCounterThisStage.clear()
        divideAmountThisStage.clear()
        mutateAmountThisStage.clear()
        justChangedStage.clear(true)

        cellArenaBase.clear(-1)
        cellArenaCapacity.clear()
        cellArenaUsed.clear()
        particleArenaBase.clear(-1)
        linkArenaBase.clear(-1)
        linkArenaCapacity.clear()
        linkArenaUsed.clear()
        neuralLinkArenaBase.clear(-1)
        neuralLinkArenaCapacity.clear()
        neuralLinkArenaUsed.clear()
        // Откатывать здесь нечего: курсор брони — это lastId самой сущности, а его
        // сбрасывает её собственный clear().
    }

    override fun onResize(oldMax: Int) {
        genomeIndex = genomeIndex.resize(-1)
        genomeSize = genomeSize.resize()
        stage = stage.resize()
        dividedTimes = dividedTimes.resize()
        mutatedTimes = mutatedTimes.resize()
        alreadyGrownUp = alreadyGrownUp.resize(false)
        divideCounterThisStage = divideCounterThisStage.resize()
        mutateCounterThisStage = mutateCounterThisStage.resize()
        divideAmountThisStage = divideAmountThisStage.resize()
        mutateAmountThisStage = mutateAmountThisStage.resize()
        justChangedStage = justChangedStage.resize(true)

        cellArenaBase = cellArenaBase.resize(-1)
        cellArenaCapacity = cellArenaCapacity.resize()
        cellArenaUsed = cellArenaUsed.resize()
        particleArenaBase = particleArenaBase.resize(-1)
        linkArenaBase = linkArenaBase.resize(-1)
        linkArenaCapacity = linkArenaCapacity.resize()
        linkArenaUsed = linkArenaUsed.resize()
        neuralLinkArenaBase = neuralLinkArenaBase.resize(-1)
        neuralLinkArenaCapacity = neuralLinkArenaCapacity.resize()
        neuralLinkArenaUsed = neuralLinkArenaUsed.resize()
    }

    companion object {
        /**
         * Пропуск между аренами, в элементах.
         *
         * 64 элемента — это 64 байта даже у ByteArray/BooleanArray, то есть гарантированно
         * не меньше кэш-линии в КАЖДОМ массиве сущности, независимо от типа элемента и от
         * того, как JVM выровняла сам объект массива. Без этого зазора последний элемент
         * арены A и первый элемент арены B делили бы линию, и два воркера, считающие
         * соседние организмы, гоняли бы её между ядрами (false sharing).
         *
         * Цена — 64 слота на организм на массив. При арене в ~1200 клеток это ~5%.
         * Для мелких организмов доля вырастет, и тогда зазор имеет смысл считать
         * по типу массива (16 элементов хватает для Float/Int), но пока организмы
         * крупные, единое число проще и надёжнее.
         */
        const val ARENA_GAP = 64

        /**
         * Запас ёмкости арены сверх размера взрослого организма, в процентах.
         *
         * Нужен потому, что переиспользования слотов внутри арены пока нет: умершая клетка
         * освобождает место физически, но не логически. Запас определяет, сколько смертей
         * организм переживёт, прежде чем упрётся в границу арены.
         */
        const val ARENA_HEADROOM_PERCENT = 30

        /**
         * Размер взрослого организма для текущего тестового генома.
         *
         * ВРЕМЕННО КОНСТАНТА. Настоящее значение выводится из генома (сколько делений даёт
         * программа роста) и должно приезжать сюда параметром allocateArenas — так же, как
         * genomeSize. До тех пор арены рассчитаны ровно на текущий тестовый организм.
         */
        const val DEFAULT_MAX_CELLS = 947

        /** Связей у взрослого организма того же генома. См. оговорку к [DEFAULT_MAX_CELLS]. */
        const val DEFAULT_MAX_LINKS = 2617

        /**
         * Нейросвязей. В отличие от клеток и связей это число НЕ измерено — оно зависит от
         * того, сколько клеток организма нейронные и как они соединены. Взято с большим
         * запасом намеренно: исчерпание арены нейросвязей проявится как молча непостроенная
         * нейросеть, а не как падение.
         */
        const val DEFAULT_MAX_NEURAL_LINKS = 400
    }
}
