package io.github.some_example_name.old.cells

import com.badlogic.gdx.graphics.Color
import io.github.some_example_name.old.core.CellSettings
import io.github.some_example_name.old.core.DIContext
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import it.unimi.dsi.fastutil.ints.IntArrayList
import kotlin.reflect.KClass

sealed class Cell(
    val defaultColor: Color,
    val cellTypeId: Int,
    val textureName: String = "not_cell.png",
    val isDirected: Boolean = false,
    val isNeural: Boolean = false,
    val maxEnergy: Float = 5f,
    val isNeuronTransportable: Boolean = true,
    val doesNeedNeuralConnections: Boolean = false,
    val effectOnContact: Boolean = false,
    val isCollidable: Boolean = true,
    val descriptionBundle: String? = null,
    val specialData: KClass<out SpecialModData> = Plug::class,
    val defaultCellSettings: CellSettings = CellSettings(
        maxEnergy = 5f,
        cellStiffness = 0.02f,
        linkStiffness = 0.025f,
        energyActionCost = 0.0005f,
    )
) {
    val name: String = this::class.simpleName ?: "UnknownCell"
    val description = descriptionBundle?.let { bundle.get(descriptionBundle) } ?: ""
    val doesItHasSpecialModData = specialData != Plug::class

    lateinit var context: DIContext

    //TODO передавать Di конеткст в методы, onStart, doOnTick, onContact и тд
    val particleEntity get() = context.particleEntity
    val cellEntity get() = context.cellEntity
    val linkEntity get() = context.linkEntity
    val neuralLinkEntity get() = context.neuralLinkEntity
    val substancesEntity get() = context.substancesEntity
    val specialEntity get() = context.specialEntity
    val worldCommandsManager get() = context.worldCommandsManager
    val organEntity get() = context.organEntity
    val genomeManager get() = context.genomeManager
    val pheromoneEntity get() = context.pheromoneEntity

    val gridManager get() = context.gridManager
    val organManager get() = context.organManager
    val pheromonesManager get() = context.pheromonesManager

    open fun onStart(cellIndex: Int, threadId: Int, genomeIndex: Int = -1) {

    }

    open fun doOnTick(cellIndex: Int, threadId: Int) {

    }

    /**
     * Вызывается из фазы коллизий (CollisionManager.repulse), т.е. изнутри обхода сетки.
     *
     * ЗАПРЕЩЕНО менять содержимое сетки: никаких gridManager.addParticle / addCell /
     * removeParticle и ничего, что меняет particleCounts, grid или mapMoreThenMax.
     * ParticlePhysicsSystem обходит слоты сетки напрямую, без копирования в промежуточный
     * массив, поэтому мутация сетки во время обхода приведёт к пропуску/дублированию
     * частиц, а при переполнении клетки — к рассинхрону particleCounts со списком-хвостом.
     *
     * Можно: писать в vx/vy/energy/radius/цвет и складывать отложенные команды в
     * worldCommandsManager.worldCommandBuffer[threadId] — они исполнятся после фаз физики.
     *
     * Также помни, что метод вызывается из нескольких потоков: писать можно только по
     * индексам участников контакта (cellIndex / particleIndexCollided) и в буфер своего
     * threadId, без общего изменяемого состояния.
     */
    open fun onContact(cellIndex: Int, particleIndexCollided: Int, distance: Float, threadId: Int) {

    }


    open fun onDie(cellIndex: Int) {

    }

    open fun onLinkDeleted(cellIndex: Int, linkIndex: Int, threadId: Int) {

    }

    protected fun getNeuralLinks(cellIndex: Int): IntArrayList {
        return if (doesNeedNeuralConnections) {
            cellEntity.neuralConnections.get(cellIndex)
        } else throw Exception("doesNeedConnections = false")
    }

}

interface SpecialModData

object Plug: SpecialModData
