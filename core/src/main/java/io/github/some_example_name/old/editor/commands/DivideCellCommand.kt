package io.github.some_example_name.old.editor.commands

import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.genomics.genome.CellAction
import io.github.some_example_name.old.systems.genomics.genome.GenomeStage
import io.github.some_example_name.old.systems.genomics.genome.LinkData
import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.editor.system.EditorLogicSystem
import io.github.some_example_name.old.editor.system.SymmetryManager
import io.github.some_example_name.old.systems.physics.GridManager
import kotlin.math.atan2
import kotlin.math.sqrt

fun getAll2LayersNeighboursEditor(
    clickedX: Float,
    clickedY: Float,
    gridManager: GridManager,
    clickedCellIndex: Int
): List<Int> {
    val gridGrabbedX = clickedX.toInt()
    val gridGrabbedY = clickedY.toInt()
    val allCells = mutableListOf<Int>()
    for (i in -2..2) {
        for (j in -2..2) {
            if (i == 2 && j == 2) continue
            if (i == -2 && j == 2) continue
            if (i == 2 && j == -2) continue
            if (i == -2 && j == -2) continue
            allCells.addAll(gridManager.getParticles(gridGrabbedX + i, gridGrabbedY + j).toList())
        }
    }
    return allCells.filter { it != clickedCellIndex }
}

fun tryToDivideCell(
    clickedCellIndex: Int,
    gridManager: GridManager,
    editorLogicSystem: EditorLogicSystem,
    symmetryManager: SymmetryManager,
    currentTick: Int,
    nextStageTick: Int
): Pair<Float, Float>? {
    val clickedCell = editorLogicSystem.toEditorData(clickedCellIndex)
    val xs = mutableListOf<Float>()
    val ys = mutableListOf<Float>()

    val neighboursAllowedForConnectionIds = getAll2LayersNeighboursEditor(
        clickedCell.x,
        clickedCell.y,
        gridManager,
        clickedCellIndex
    )

    neighboursAllowedForConnectionIds.forEach { it ->
        val clickedCell = editorLogicSystem.toEditorData(it)
        xs.add(clickedCell.x)
        ys.add(clickedCell.y)
    }

    val newPoint = symmetryManager.newPoint(clickedCell, xs, ys, currentTick, nextStageTick)
    return newPoint
}

class DivideCellCommand(
    val clickedCell: EditorCell,
    val neighboursCells: List<EditorCell>,
    val divide: Action,
    val newId: Int,
    val newPoint: Pair<Float, Float>,
    val doesNeedAddNewStage: Boolean,
    val genomeStageInstruction: MutableList<GenomeStage>,
    val currentStage: Int,
    val autoLinking: Boolean
) : Command {

    override var stage = currentStage

    private val oldGenomeStageInstruction = genomeStageInstruction.map { it.deepCopy() }
    private var newGenomeStageInstruction: List<GenomeStage>? = null

    override fun execute() {

        if (newGenomeStageInstruction != null) {
            genomeStageInstruction.clear()
            genomeStageInstruction.addAll(newGenomeStageInstruction!!)
            return
        }

        val justAddedCellX = newPoint.first
        val justAddedCellY = newPoint.second

        val deltaXAngle = justAddedCellX - clickedCell.x
        val deltaYAngle = justAddedCellY - clickedCell.y

        val angle = atan2(deltaYAngle, deltaXAngle) - clickedCell.angleToParent

        val physicalLink = if (autoLinking) HashMap(neighboursCells.associate {
            val deltaX = justAddedCellX - it.x
            val deltaY = justAddedCellY - it.y
            val length = sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
            it.id to LinkData(length = length)
        }) else HashMap()

        if (doesNeedAddNewStage) {
            genomeStageInstruction.add(GenomeStage())
        }
        val divideAction = divide.copy(
            id = newId,
            angle = angle,
            physicalLink = physicalLink
        )

        genomeStageInstruction[currentStage].cellActions.compute(clickedCell.id) { _, oldValue ->
            oldValue?.copy(divide = divideAction)
                ?: CellAction(
                    divide = divideAction
                )
        }
        newGenomeStageInstruction = genomeStageInstruction.map { it.deepCopy() }
    }

    override fun undo() {
        genomeStageInstruction.clear()
        genomeStageInstruction.addAll(oldGenomeStageInstruction)
    }
}
