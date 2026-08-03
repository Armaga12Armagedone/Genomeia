package io.github.some_example_name.old.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.utils.Disposable
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisTextButton
import io.github.some_example_name.old.cells.base.CellListBuilder
import io.github.some_example_name.old.commands.UserCommandManager
import io.github.some_example_name.old.commands.WorldCommandsManager
import io.github.some_example_name.old.core.DIGameGlobalContainer.genomeJsonReader
import io.github.some_example_name.old.core.DIGameGlobalContainer.shaderManager
import io.github.some_example_name.old.core.DIGameGlobalContainer.substrateSettings
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.EyeEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.NeuralEntity
import io.github.some_example_name.old.entities.OrganEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.PheromoneEmitterEntity
import io.github.some_example_name.old.entities.PheromoneEntity
import io.github.some_example_name.old.entities.ProducerEntity
import io.github.some_example_name.old.entities.SpecialEntity
import io.github.some_example_name.old.entities.SpecialModDataEntity
import io.github.some_example_name.old.systems.simulation.SimulationData
import io.github.some_example_name.old.entities.SubstancesEntity
import io.github.some_example_name.old.entities.TailEntity
import io.github.some_example_name.old.systems.pheromone.PheromonesManager
import io.github.some_example_name.old.systems.genomics.CellSystem
import io.github.some_example_name.old.systems.genomics.DivideManager
import io.github.some_example_name.old.systems.genomics.MutateManager
import io.github.some_example_name.old.systems.genomics.OrganManager
import io.github.some_example_name.old.systems.genomics.genome.GenomeManager
import io.github.some_example_name.old.systems.physics.GridManager
import io.github.some_example_name.old.systems.physics.LinkPhysicsSystem
import io.github.some_example_name.old.systems.physics.ParticlePhysicsSystem
import io.github.some_example_name.old.systems.render.RenderBufferManager
import io.github.some_example_name.old.systems.render.RenderSystem
import io.github.some_example_name.old.systems.simulation.SimulationSystem
import io.github.some_example_name.old.systems.simulation.ThreadManager
import io.github.some_example_name.old.features.settings.GlobalSettings.GRID_HEIGHT
import io.github.some_example_name.old.features.settings.GlobalSettings.GRID_WIDTH
import io.github.some_example_name.old.features.worldeditor.WorldTerrainManager
import io.github.some_example_name.old.systems.maps.MapSave
import io.github.some_example_name.old.systems.physics.CollisionManager
import io.github.some_example_name.old.systems.physics.MovementManager
import kotlin.getValue

object DISimulationContainer: DIContext, Disposable {

    override var gridWidth = 128
    override var gridHeight = 128
    const val HALF_CHUNK_HEIGHT = 4 // Also max particle speed
    var chunkHeight = HALF_CHUNK_HEIGHT * 2
    var heightMultiplier = chunkHeight * 2
    var gridSize = gridWidth * gridHeight
    override var threadCount = (gridHeight / chunkHeight) / 2
    override var totalChunks = threadCount * 2
    override var chunkSize = gridSize / totalChunks

    var energyTransportRate = substrateSettings.data.rateOfEnergyTransferInLinks
    var linkMaxLength2 = 3f * 3f
    var cellsSettings = substrateSettings.cellsSettings

    val baseMapDir = "./maps/"

    override var gridManager = GridManager(
        gridWidth = gridWidth,
        gridHeight = gridHeight,
        diContext = this,
        maxAmountOfParticles = 4
    )

    private val cellListBuilder = CellListBuilder().apply {
        bindToDIContext(this@DISimulationContainer)
    }
    val cellList = cellListBuilder.instances
    val zygote = cellListBuilder.zygote

    var tailEntity = TailEntity(tailStartMaxAmount = 1_000)
    override var organEntity = OrganEntity(organStartMaxAmount = 400)
    val simulationData = SimulationData()

    override var particleEntity = ParticleEntity(
        particlesStartMaxAmount = 30_000,
        gridManager = gridManager
    )
    var neuralEntity = NeuralEntity(neuralStartMaxAmount = 10_000, cellList = cellList)
    var eyeEntity = EyeEntity(eyeStartMaxAmount = 3_000)
    var producerEntity = ProducerEntity(producerStartMaxAmount = 100)
    var specialModDataEntity = SpecialModDataEntity(specialModDataStartMaxAmount = 100)
    var pheromoneEmitterEntity = PheromoneEmitterEntity(pheromoneEmitterStartMaxAmount = 100)

    override var specialEntity = SpecialEntity(
        cellsStartMaxAmount = 10_000,
        eyeEntity = eyeEntity,
        tailEntity = tailEntity,
        specialModDataEntity = specialModDataEntity,
        producerEntity = producerEntity,
        pheromoneEmitterEntity = pheromoneEmitterEntity
    )

    override var cellEntity = CellEntity(
        cellsStartMaxAmount = 10_000,
        particleEntity = particleEntity,
        simulationData = simulationData,
        substrateSettings = substrateSettings,
        cellList = cellList,
        neuralEntity = neuralEntity,
        specialEntity = specialEntity
    )

    override var linkEntity = LinkEntity(
        20_000,
        cellEntity = cellEntity,
        gridManager = gridManager,
        particleEntity = particleEntity,
        diContext = this
    )

    override var pheromoneEntity = PheromoneEntity(gridManager = gridManager)

    override var substancesEntity = SubstancesEntity(
        startMaxAmount = 5_000,
        particleEntity = particleEntity,
        substrateSettings = substrateSettings
    )

    // КРИТИЧЕСКИ ВАЖНО: entityList должен всегда возвращать актуальные ссылки на сущности!
    override val entityList: List<io.github.some_example_name.old.entities.Entity>
        get() = listOf(
            tailEntity, organEntity, particleEntity, neuralEntity, eyeEntity,
            specialModDataEntity, specialEntity, cellEntity, linkEntity,
            pheromoneEntity, substancesEntity, producerEntity, pheromoneEmitterEntity
        )

    override val genomeManager = GenomeManager(
        genomeJsonReader = genomeJsonReader,
        simulationData = simulationData
    )

    // Системы переведены в var для возможности их пересоздания в reInit()
    override var organManager = OrganManager(
        organEntity = organEntity,
        genomeManager = genomeManager,
        cellEntity = cellEntity
    )

    var renderBufferManager = RenderBufferManager(
        simulationData = simulationData,
        cellEntity = cellEntity,
        particleEntity = particleEntity,
        linkEntity = linkEntity,
        cellList = cellList,
        specialEntity = specialEntity,
        pheromoneEntity = pheromoneEntity
    )

    var renderSystem = RenderSystem(
        cellEntity = cellEntity,
        linkEntity = linkEntity,
        shaderManager = shaderManager,
        particleEntity = particleEntity,
        renderBufferManager = renderBufferManager,
        pheromoneEntity = pheromoneEntity
    )

    var userCommandManager = UserCommandManager(
        organEntity = organEntity,
        cellEntity = cellEntity,
        genomeManager = genomeManager,
        cellList = cellList,
        simulationData = simulationData,
        gridManager = gridManager,
        particleEntity = particleEntity,
        zygote = zygote,
        isEditor = false
    )

    override var worldCommandsManager = WorldCommandsManager(
        gridManager = gridManager,
        organManager = organManager,
        organEntity = organEntity,
        cellEntity = cellEntity,
        linkEntity = linkEntity,
        particleEntity = particleEntity,
        pheromoneEntity = pheromoneEntity,
        substrateSettings = substrateSettings,
        genomeManager = genomeManager,
        simulationData = simulationData,
        cellList = cellList,
        substancesEntity = substancesEntity,
        specialEntity = specialEntity,
        userCommandManager = userCommandManager,
        diContext = this,
        isEditor = false
    )

    override var pheromonesManager = PheromonesManager(
        pheromoneEntity = pheromoneEntity,
        worldCommandsManager = worldCommandsManager,
        particleEntity = particleEntity,
        cellEntity = cellEntity
    )

    override val mapSave = MapSave()

    var worldTerrainManager = WorldTerrainManager(
        particleEntity = particleEntity,
        substancesEntity = substancesEntity
    )

    var collisionManager = CollisionManager(
        entity = particleEntity,
        worldCommandsManager = worldCommandsManager,
        linkEntity = linkEntity,
        cellList = cellList,
        cellEntity = cellEntity,
        substancesEntity = substancesEntity,
    )

    var particlePhysicsSystem = ParticlePhysicsSystem(
        entity = particleEntity,
        gridManager = gridManager,
        substrateSettings = substrateSettings,
        worldCommandsManager = worldCommandsManager,
        simulationData = simulationData,
        linkEntity = linkEntity,
        cellList = cellList,
        cellEntity = cellEntity,
        substancesEntity = substancesEntity,
        pheromonesManager = pheromonesManager,
        collisionManager = collisionManager
    )

    val threadManager = ThreadManager(simulationData = simulationData)

    var divideManager = DivideManager(
        cellEntity = cellEntity,
        worldCommandsManager = worldCommandsManager,
        particleEntity = particleEntity,
        gridManager = gridManager,
        cellList = cellList
    )

    var mutateManager = MutateManager(
        cellEntity = cellEntity,
        linkEntity = linkEntity,
        worldCommandsManager = worldCommandsManager,
        particleEntity = particleEntity,
        gridManager = gridManager,
        specialEntity = specialEntity,
        organEntity = organEntity,
        isEditor = false
    )

    var cellSystem = CellSystem(
        cellEntity = cellEntity,
        linkEntity = linkEntity,
        organEntity = organEntity,
        genomeManager = genomeManager,
        worldCommandsManager = worldCommandsManager,
        gridManager = gridManager,
        divideManager = divideManager,
        mutateManager = mutateManager,
        threadManager = threadManager
    )

    var linkPhysicsSystem = LinkPhysicsSystem(
        linkEntity = linkEntity,
        substrateSettings = substrateSettings,
        particleEntity = particleEntity,
        cellEntity = cellEntity,
        worldCommandsManager = worldCommandsManager,
        cellSystem = cellSystem,
        diContext = this
    )

    var movementManager = MovementManager(
        entity = particleEntity,
        gridManager = gridManager,
        substrateSettings = substrateSettings,
        worldCommandsManager = worldCommandsManager,
        simulationData = simulationData,
        linkEntity = linkEntity,
        cellList = cellList,
        cellEntity = cellEntity,
        substancesEntity = substancesEntity,
        pheromonesManager = pheromonesManager
    )

    // simulationSystem переведен в lateinit var
    var simulationSystem: SimulationSystem = SimulationSystem(
        gridManager = gridManager,
        worldCommandsManager = worldCommandsManager,
        organManager = organManager,
        organEntity = organEntity,
        cellEntity = cellEntity,
        linkEntity = linkEntity,
        particleEntity = particleEntity,
        pheromoneEntity = pheromoneEntity,
        substrateSettings = substrateSettings,
        threadManager = threadManager,
        genomeManager = genomeManager,
        particlePhysicsSystem = particlePhysicsSystem,
        linkPhysicsSystem = linkPhysicsSystem,
        simulationData = simulationData,
        cellSystem = cellSystem,
        userCommandManager = userCommandManager,
        entityList = entityList,
        renderBufferManager = renderBufferManager,
        pheromonesManager = pheromonesManager,
        movementManager = movementManager,
        worldTerrainManager = worldTerrainManager
    )

    // init блок перенесен в самый конец, чтобы все var поля уже были объявлены
    init {
        if (gridHeight % heightMultiplier != 0) throw Exception("gridHeight should be a multiple of (halfChunkHeight * 2 * 2)")
        println("thread count: $threadCount")
        println("thread count: $heightMultiplier")

        // Первичная инициализация всех систем
//        reInit()
    }

    /**
     * Пересоздает все системы, чтобы они подхватили новые ссылки на сущности.
     * Должен вызываться после загрузки карты и замены сущностей в DISimulationContainer.
     */
    fun reInit() {
        // 1. Пересоздаем GridManager
        gridManager = GridManager(
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            diContext = this,
            maxAmountOfParticles = 4
        )

        // 2. Managers
        organManager = OrganManager(
            organEntity = organEntity,
            genomeManager = genomeManager,
            cellEntity = cellEntity
        )

        userCommandManager = UserCommandManager(
            organEntity = organEntity,
            cellEntity = cellEntity,
            genomeManager = genomeManager,
            cellList = cellList,
            simulationData = simulationData,
            gridManager = gridManager,
            particleEntity = particleEntity,
            zygote = zygote,
            isEditor = false
        )

        worldCommandsManager = WorldCommandsManager(
            gridManager = gridManager,
            organManager = organManager,
            organEntity = organEntity,
            cellEntity = cellEntity,
            linkEntity = linkEntity,
            particleEntity = particleEntity,
            pheromoneEntity = pheromoneEntity,
            substrateSettings = substrateSettings,
            genomeManager = genomeManager,
            simulationData = simulationData,
            cellList = cellList,
            substancesEntity = substancesEntity,
            specialEntity = specialEntity,
            userCommandManager = userCommandManager,
            diContext = this,
            isEditor = false
        )

        pheromonesManager = PheromonesManager(
            pheromoneEntity = pheromoneEntity,
            worldCommandsManager = worldCommandsManager,
            particleEntity = particleEntity,
            cellEntity = cellEntity
        )

        worldTerrainManager = WorldTerrainManager(
            particleEntity = particleEntity,
            substancesEntity = substancesEntity
        )

        collisionManager = CollisionManager(
            entity = particleEntity,
            worldCommandsManager = worldCommandsManager,
            linkEntity = linkEntity,
            cellList = cellList,
            cellEntity = cellEntity,
            substancesEntity = substancesEntity,
        )

        // 3. Physics & Genomics
        particlePhysicsSystem = ParticlePhysicsSystem(
            entity = particleEntity,
            gridManager = gridManager,
            substrateSettings = substrateSettings,
            worldCommandsManager = worldCommandsManager,
            simulationData = simulationData,
            linkEntity = linkEntity,
            cellList = cellList,
            cellEntity = cellEntity,
            substancesEntity = substancesEntity,
            pheromonesManager = pheromonesManager,
            collisionManager = collisionManager
        )

        divideManager = DivideManager(
            cellEntity = cellEntity,
            worldCommandsManager = worldCommandsManager,
            particleEntity = particleEntity,
            gridManager = gridManager,
            cellList = cellList
        )

        mutateManager = MutateManager(
            cellEntity = cellEntity,
            linkEntity = linkEntity,
            worldCommandsManager = worldCommandsManager,
            particleEntity = particleEntity,
            gridManager = gridManager,
            specialEntity = specialEntity,
            organEntity = organEntity,
            isEditor = false
        )

        cellSystem = CellSystem(
            cellEntity = cellEntity,
            linkEntity = linkEntity,
            organEntity = organEntity,
            genomeManager = genomeManager,
            worldCommandsManager = worldCommandsManager,
            gridManager = gridManager,
            divideManager = divideManager,
            mutateManager = mutateManager,
            threadManager = threadManager
        )

        linkPhysicsSystem = LinkPhysicsSystem(
            linkEntity = linkEntity,
            substrateSettings = substrateSettings,
            particleEntity = particleEntity,
            cellEntity = cellEntity,
            worldCommandsManager = worldCommandsManager,
            cellSystem = cellSystem,
            diContext = this
        )

        movementManager = MovementManager(
            entity = particleEntity,
            gridManager = gridManager,
            substrateSettings = substrateSettings,
            worldCommandsManager = worldCommandsManager,
            simulationData = simulationData,
            linkEntity = linkEntity,
            cellList = cellList,
            cellEntity = cellEntity,
            substancesEntity = substancesEntity,
            pheromonesManager = pheromonesManager
        )

        // 4. Render
        renderBufferManager = RenderBufferManager(
            simulationData = simulationData,
            cellEntity = cellEntity,
            particleEntity = particleEntity,
            linkEntity = linkEntity,
            cellList = cellList,
            specialEntity = specialEntity,
            pheromoneEntity = pheromoneEntity
        )

        renderSystem = RenderSystem(
            cellEntity = cellEntity,
            linkEntity = linkEntity,
            shaderManager = shaderManager,
            particleEntity = particleEntity,
            renderBufferManager = renderBufferManager,
            pheromoneEntity = pheromoneEntity
        )

        // 5. Main Simulation System
        simulationSystem = SimulationSystem(
            gridManager = gridManager,
            worldCommandsManager = worldCommandsManager,
            organManager = organManager,
            organEntity = organEntity,
            cellEntity = cellEntity,
            linkEntity = linkEntity,
            particleEntity = particleEntity,
            pheromoneEntity = pheromoneEntity,
            substrateSettings = substrateSettings,
            threadManager = threadManager,
            genomeManager = genomeManager,
            particlePhysicsSystem = particlePhysicsSystem,
            linkPhysicsSystem = linkPhysicsSystem,
            simulationData = simulationData,
            cellSystem = cellSystem,
            userCommandManager = userCommandManager,
            entityList = entityList,
            renderBufferManager = renderBufferManager,
            pheromonesManager = pheromonesManager,
            movementManager = movementManager,
            worldTerrainManager = worldTerrainManager
        )
    }

    override fun dispose() {
        TODO("Not yet implemented")
    }

    fun resizeWorld() {
        if (GRID_WIDTH == gridWidth && GRID_HEIGHT == gridHeight) return
        gridWidth = GRID_WIDTH
        gridHeight = GRID_HEIGHT

        chunkHeight = HALF_CHUNK_HEIGHT * 2
        heightMultiplier = chunkHeight * 2
        gridSize = gridWidth * gridHeight
        threadCount = (gridHeight / chunkHeight) / 2
        totalChunks = threadCount * 2
        chunkSize = gridSize / totalChunks
        if (gridHeight % heightMultiplier != 0) throw Exception("gridHeight should be a multiple of (halfChunkHeight * 2 * 2)")
        gridManager.resize()
        cellListBuilder.resize()
        threadManager.resize()
        worldCommandsManager.resize()
    }
}
