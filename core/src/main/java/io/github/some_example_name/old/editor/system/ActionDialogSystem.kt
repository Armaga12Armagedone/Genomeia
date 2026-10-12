package io.github.some_example_name.old.editor.system

import com.badlogic.gdx.graphics.Color
import io.github.some_example_name.old.cells.Eye
import io.github.some_example_name.old.cells.PheromoneEmitter
import io.github.some_example_name.old.cells.PheromoneSensor
import io.github.some_example_name.old.cells.Synapse
import io.github.some_example_name.old.cells.base.formulaType
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.cellList
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.cellsTypeNames
import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.features.editor.dialog.ActionDialogType
import io.github.some_example_name.old.features.editor.dialog.getColorFromBits
import io.github.some_example_name.old.systems.genomics.genome.Action
import kotlin.math.atan2
import kotlin.math.sqrt

class ActionDialogSystem(
    actionDialogType: ActionDialogType,
    newDividedCellPosition: Pair<Float, Float>?,
    val clickedCell: EditorCell,
) {

    var action = when(actionDialogType) {
        ActionDialogType.DIVIDE -> Action(cellType = 0, color = getCellColor(0))
        ActionDialogType.MUTATION -> clickedCell.mutate?.copy() ?: Action()
        ActionDialogType.CHANGE_REMOVE -> clickedCell.divide?.copy() ?: throw Exception("clickedCell.divide is null")
    }

    val angleParent = when(actionDialogType) {
        ActionDialogType.DIVIDE -> {
            if (newDividedCellPosition != null) {
                val dx = newDividedCellPosition.first - clickedCell.x
                val dy = newDividedCellPosition.second - clickedCell.y

                val len = sqrt(dx * dx + dy * dy)

                val angleCos = dx / len
                val angleSin = dy / len
                atan2(angleSin, angleCos)
            } else throw Exception("newDividedCellPosition is null")
        }
        ActionDialogType.MUTATION -> clickedCell.angleToParent
        ActionDialogType.CHANGE_REMOVE -> clickedCell.angleToParent
    }

    val angleDirected get() = action.angleDirected ?: clickedCell.angleDirected ?: throw Exception("angleDirected is null")

    val cellType get() = action.cellType ?: clickedCell.actual?.cellType ?: throw Exception("cellType is null")

    /** Изменение дефолтных параметров в action при изменении типка клетки */
    fun setUpDefaultActionData(fromCellType: Int, toCellType: Int) {
        action = action.copy(
            cellType = toCellType,
            color = getCellColor(toCellType)
        )

        when {
            fromCellType.isDirected() && !isDirected() -> {
                action = action.copy(angleDirected = null)
            }
            !fromCellType.isDirected() && isDirected() -> {
                action = action.copy(angleDirected = 0f)
            }
        }

        // У синапса в a, b, c лежат параметры обучения, а не коэффициенты формулы, поэтому
        // при переходе между синапсом и обычным нейроном они тоже сбрасываются.
        when {
            fromCellType.isNeural() && !isNeural() -> {
                action = action.copy(
                    funActivation = null,
                    a = null,
                    b = null,
                    c = null,
                    isSum = null
                )
            }
            isSynapse() && !fromCellType.isSynapse() -> {
                action = action.copy(
                    funActivation = 0,
                    a = Synapse.DEFAULT_DECAY_RATE,
                    b = Synapse.DEFAULT_LEARNING_RATE,
                    c = Synapse.DEFAULT_DEPRESSION,
                    isSum = true
                )
            }
            isNeural() && (!fromCellType.isNeural() || fromCellType.isSynapse()) -> {
                action = action.copy(
                    funActivation = 0,
                    a = 1f,
                    b = 0f,
                    c = 0f,
                    isSum = true
                )
            }
        }

        when {
            fromCellType.isEye() && !isEye() -> {
                action = action.copy(
                    colorRecognition = null,
                    lengthDirected = null
                )
            }
            !fromCellType.isEye() && isEye() -> {
                action = action.copy(
                    colorRecognition = 7,
                    lengthDirected = 4.25f
                )
            }
        }

        when {
            fromCellType.isPheromone() && !isPheromone() -> {
                action = action.copy(
                    pheromoneType = null
                )
            }
            !fromCellType.isPheromone() && isPheromone() -> {
                action = action.copy(
                    pheromoneType = 0,
                )
            }
        }
    }

    fun isEye() = cellList[cellType] is Eye
    fun isDirected() = cellList[cellType].isDirected
    fun isNeural() = cellList[cellType].isNeural
    fun isSynapse() = cellList[cellType] is Synapse
    fun isPheromone() = cellList[cellType] is PheromoneEmitter || cellList[cellType] is PheromoneSensor
    fun getCellColor(): Color = cellList[cellType].defaultColor.cpy()


    /** Формирование списка изменений при мутации*/
    fun makeMutateList(): String {
        val text = StringBuilder()
        val actual = clickedCell.actual ?: return ""
        clickedCell.mutate?.apply {

            // Мутация без смены типа хранит cellType = null — тип остаётся текущим.
            val actualCellType = actual.cellType
            val resultCellType = cellType ?: actualCellType
            if (resultCellType != null && actualCellType != null && resultCellType != actualCellType) {
                text.append("Cell type: ${cellsTypeNames[actualCellType]} -> ${cellsTypeNames[resultCellType]}\n")
            }
            val toSynapse = resultCellType?.isSynapse() == true

            // Синапс не вызывает функцию активации и не складывает входы — эти строки ему ни к чему.
            if (!toSynapse && funActivation != null && funActivation != actual.funActivation) {
                val formula = if (actual.funActivation != null) formulaType[actual.funActivation] else null
                text.append("Activation formula: $formula -> ${formulaType[funActivation]}\n")
            }

            val aName = if (toSynapse) bundle.get("synapse.decayRate") else "a"
            val bName = if (toSynapse) bundle.get("synapse.learningRate") else "b"
            val cName = if (toSynapse) bundle.get("synapse.depression") else "c"
            if (a != null && a != actual.a) text.append("$aName: ${actual.a} -> $a\n")
            if (b != null && b != actual.b) text.append("$bName: ${actual.b} -> $b\n")
            if (c != null && c != actual.c) text.append("$cName: ${actual.c} -> $c\n")
            if (!toSynapse && isSum != null && isSum != actual.isSum) {
                val fromText = when (actual.isSum) {
                    true -> "Addition"
                    false -> "Multiplication"
                    null -> "null"
                }
                val toText = if (isSum) "Addition\n" else "Multiplication\n"
                text.append("isSum: $fromText -> $toText")
            }

            if (colorRecognition != null && colorRecognition != actual.colorRecognition) {
                val colorFrom = if (actual.colorRecognition != null) getColorFromBits(actual.colorRecognition) else null
                val colorTo = getColorFromBits(colorRecognition)
                val fromText = if (colorFrom != null) "(r:${if (colorFrom.r > 0) 1 else 0}, g:${if (colorFrom.g > 0) 1 else 0}, b${if (colorFrom.b > 0) 1 else 0})" else null
                text.append("Eye color recognition: $fromText -> (r:${if (colorTo.r > 0) 1 else 0}, g:${if (colorTo.g > 0) 1 else 0}, b${if (colorTo.b > 0) 1 else 0})\n")
            }

            if (lengthDirected != null && lengthDirected != actual.lengthDirected) {
                text.append("Eye distance: $lengthDirected -> ${actual.lengthDirected}\n")
            }


            if (pheromoneType != null && pheromoneType != actual.pheromoneType) {
                text.append("Pheromone type: ${actual.pheromoneType} -> $pheromoneType\n")
            }

        }

        return text.toString()
    }
}

fun Int.isEye() = cellList[this] is Eye
fun Int.isDirected() = cellList[this].isDirected
fun Int.isNeural() = cellList[this].isNeural
fun Int.isSynapse() = cellList[this] is Synapse
fun Int.isPheromone() = cellList[this] is PheromoneEmitter || cellList[this] is PheromoneSensor
fun getCellColor(cellType: Int) = cellList[cellType].defaultColor.cpy()
