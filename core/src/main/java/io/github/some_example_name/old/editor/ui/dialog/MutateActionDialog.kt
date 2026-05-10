package io.github.some_example_name.old.editor.ui.dialog

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.I18NBundle
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisSlider
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.color.ColorPickerAdapter
import io.github.some_example_name.old.cells.base.formulaType
import io.github.some_example_name.old.core.color_picker.ColorPicker
import io.github.some_example_name.old.core.utils.invSqrt
import io.github.some_example_name.old.editor.entities.CellReplay
import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.editor.entities.EyeReplay
import io.github.some_example_name.old.editor.entities.NeuralReplay
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.ui.dialogs.setupTitleSize
import io.github.some_example_name.old.ui.screens.MyGame
import io.github.some_example_name.old.ui.screens.applyCustomFontMedium
import kotlin.math.atan2


fun getColorFromBits(bits: Int): Color {
    if (bits == 0) return Color.BLACK.cpy()

    var r = 0f
    var g = 0f
    var b = 0f
    var count = 0

    if (bits and 1 != 0) {
        r += 1f
        count++
    }
    if (bits and 2 != 0) {
        g += 1f
        count++
    }
    if (bits and 4 != 0) {
        b += 1f
        count++
    }

    return Color(r / count, g / count, b / count, 1f)
}

fun encodeColorToBits(r: Float, g: Float, b: Float): Int {
    var bits = 0
    if (r == 1f) bits = bits or 1
    if (g == 1f) bits = bits or 2
    if (b == 1f) bits = bits or 4
    return bits
}

class MutateActionDialog(
    val clickedCell: EditorCell,
    val parentCell: EditorCell?,
    val startCurrentStageTick: Int,
    val cellReplay: CellReplay,
    val eyeReplay: EyeReplay,
    val neuralReplay: NeuralReplay,
    val clickedIndex: Int,
    val onMutate: (Action) -> Unit,
    val game: MyGame,
    val bundle: I18NBundle
) : VisDialog("${bundle.get("button.cellId")} ${clickedCell.id}") {

    private val colorOfCellFrom = Color().also {
        val argb = cellReplay.getColor(startCurrentStageTick, clickedIndex)
        val rgba = ((argb shr 16) and 0xFF) or (argb and 0xFF00) or ((argb shl 16) and 0xFF0000) or (argb and -0x1000000)
        Color.argb8888ToColor(it,  rgba)
    }

    fun getCellType() = cellReplay.getCellType(startCurrentStageTick, clickedIndex).toInt()

    fun getColorDifferentiation(tick: Int): Byte? {
        val eyeIndex = cellReplay.getSpecialTypeIndexes(tick, clickedIndex)

        return eyeReplay.getColorDifferentiation(tick, eyeIndex)
    }

    fun getVisibilityRange(tick: Int): Float? {
        val eyeIndex = cellReplay.getSpecialTypeIndexes(tick, clickedIndex)

        return eyeReplay.getVisibilityRange(tick, eyeIndex)
    }

    fun getActivationFuncType(): Byte? {
        val neuralIndex = cellReplay.getNeuralIndexes(startCurrentStageTick, clickedIndex)

        return neuralReplay.getActivationFuncType(startCurrentStageTick, neuralIndex)
    }
    fun getA(): Float? {
        val neuralIndex = cellReplay.getNeuralIndexes(startCurrentStageTick, clickedIndex)

        return neuralReplay.getA(startCurrentStageTick, neuralIndex)
    }
    fun getB(): Float? {
        val neuralIndex = cellReplay.getNeuralIndexes(startCurrentStageTick, clickedIndex)

        return neuralReplay.getB(startCurrentStageTick, neuralIndex)
    }
    fun getC(): Float? {
        val neuralIndex = cellReplay.getNeuralIndexes(startCurrentStageTick, clickedIndex)

        return neuralReplay.getC(startCurrentStageTick, neuralIndex)
    }
    fun getIsSum(): Boolean? {
        val neuralIndex = cellReplay.getNeuralIndexes(startCurrentStageTick, clickedIndex)

        return neuralReplay.getIsSum(startCurrentStageTick, neuralIndex)
    }

    fun getBaseAngleFromParent(): Float {
        if (parentCell != null) {
            val dx = clickedCell.x - parentCell.x
            val dy = clickedCell.y - parentCell.y

            val len = 1f / invSqrt(dx * dx + dy * dy)
            val toChildCos = dx / len
            val toChildSin = dy / len

            return atan2(toChildSin, toChildCos)
        } else {
            return 0f
        }
    }

    private var colorOfCellTo = colorOfCellFrom
    private var cellType = clickedCell.mutate?.cellType ?: getCellType()//cellEntity.cellType[clickedIndex].toInt()

    private var mutation: Action? = clickedCell.mutate?.copy()

    val scrollPane: ScrollPane
    init {
        VisUI.getSizes().scaleFactor = Gdx.graphics.density
        setupTitleSize(game)
        isModal = true
        isMovable = true

        val scrollContentTable = VisTable()

        // Оборачиваем в ScrollPane // Wrap it in a ScrollPane
        scrollPane = ScrollPane(scrollContentTable).apply {
            setFadeScrollBars(false)      // полоска прокрутки всегда видна // the scroll bar is always visible
            setScrollingDisabled(false, false)
            setForceScroll(false, true)
            setFlickScroll(true)
            setOverscroll(false, true)
        }

        // Добавляем ScrollPane в диалог с ограничением по высоте
        // Adding a ScrollPane to a Dialog with a Height Limit
        contentTable.add(scrollPane)
            .grow()  // растягиваем на доступное место // we stretch it to an accessible place
            .maxHeight(Gdx.graphics.height * 0.8f)

        contentTable.row()

        closeOnEscape()

        setupUI(scrollContentTable)
        pack()
        centerWindow()
    }

    private fun makeMutateList(text: StringBuilder) {
        clickedCell.mutate?.apply {
            cellType?.let {
                val fullReplayCellType = getCellType()
                if (it != fullReplayCellType)
                    text.append("Cell type: ${cellsType[fullReplayCellType]} -> ${cellsType[it]}\n")
            }
            funActivation?.let {
                val activationFuncType = getActivationFuncType()?.toInt()
                if (it != activationFuncType) {
                    val formula = if (activationFuncType != null) formulaType[activationFuncType] else null
                    text.append("Activation formula:\n${formula} -> ${formulaType[it]}\n")
                }
            }
            a?.let {
                if (it != getA())
                    text.append("a: ${getA()} -> $it\n")
            }
            b?.let {
                if (it != getB())
                    text.append("b: ${getB()} -> $it\n")
            }
            c?.let {
                if (it != getC())
                    text.append("c: ${getC()} -> $it\n")
            }
            isSum?.let {
                if (it != getIsSum())
                    text.append(
                        "isSum: ${
                            when (getIsSum()) {
                                true -> "Addition"
                                false -> "Multiplication"
                                null -> "null"
                            }
                        } -> ${
                            if (it) "Addition\n" else "Multiplication\n"
                        }"
                    )
            }
            colorRecognition?.let {
                val colorDifferentiation = getColorDifferentiation(startCurrentStageTick)?.toInt()
                val colorFrom = if (colorDifferentiation != null) getColorFromBits(colorDifferentiation) else null
                val colorTo = getColorFromBits(it)
                if (it != colorDifferentiation) {
                    val fromText =
                        if (colorFrom != null) "(r:${if (colorFrom.r > 0) 1 else 0}, g:${if (colorFrom.g > 0) 1 else 0}, b${if (colorFrom.b > 0) 1 else 0})" else null
                    text.append(
                        "Eye color recognition: $fromText -> (r:${if (colorTo.r > 0) 1 else 0}, g:${if (colorTo.g > 0) 1 else 0}, b${if (colorTo.b > 0) 1 else 0})\n"
                    )
                }
            }
            lengthDirected?.let {
                if (it != getVisibilityRange(startCurrentStageTick))
                    text.append("Eye distance: ${getVisibilityRange(startCurrentStageTick)} -> $it\n")
            }
        }
    }

    private fun setupUI(scrollContentTable: VisTable) {
        val density = Gdx.graphics.density
        scrollContentTable.clear()
        val circleWidgetFrom = CircleWidget(
            initialColor = colorOfCellFrom,
            smallCircleRadius = 3f,
            initialDirectedAngle = if (getCellType().isDirected()) {
                atan2(
                    cellReplay.getAngleSin(startCurrentStageTick,clickedIndex),
                    cellReplay.getAngleCos(startCurrentStageTick,clickedIndex)
                )
            } else null
        )
        var mutableCircleWidget = circleWidgetFrom
        val previewTable = Table()
        previewTable.add(circleWidgetFrom).size(100f * density, 100f * density)
        circleWidgetFrom.setCircleColor(colorOfCellFrom)

        val text = StringBuilder()
        if (mutation == null) {
            text.append(cellsType[cellType])
        } else {
            val volumeLabel = VisLabel("->")
            previewTable.add(volumeLabel).align(Align.center)
            mutation?.color?.let {
                colorOfCellTo = it
            }
            val circleWidgetTo = CircleWidget(
                initialColor = colorOfCellTo,
                smallCircleRadius = 3f,
                initialDirectedAngle = if (cellType.isDirected()) {
                    getBaseAngleFromParent() + (mutation?.angleDirected ?: 0f)
                } else null
            )
            previewTable.add(circleWidgetTo).size(100f * density, 100f * density)
            circleWidgetTo.setCircleColor(colorOfCellTo)
            mutableCircleWidget = circleWidgetTo
            makeMutateList(text)
        }

        scrollContentTable.add(previewTable).row()

        val description = VisLabel(text)
        game.applyCustomFontMedium(description)
        scrollContentTable.add(description).align(Align.left).row()

        val colorPicker = ColorPicker(
            game = game,
            title = bundle.get("button.chooseColorDialog"),
            listener = object : ColorPickerAdapter() {
                override fun changed(newColor: Color) {
                    colorOfCellTo = newColor.cpy()
                    mutableCircleWidget.setCircleColor(colorOfCellTo)
                }

                override fun finished(newColor: Color?) {
                    super.finished(newColor)
                    if (newColor == null) return
                    colorOfCellTo = newColor.cpy()
                    mutableCircleWidget.setCircleColor(colorOfCellTo)
                    if (mutation == null) mutation = Action()
                    mutation = mutation?.copy(color = newColor.cpy())
                }
            },
            colorInit = colorOfCellTo.cpy()
        )

        colorPicker(colorPicker, game, bundle).also { scrollContentTable.add(it).align(Align.left).padBottom(15f * density).row() }
        cellTypePicker(cellType, game) {
            if (mutation == null) mutation = Action()
            setupMutation(cellType, it, mutableCircleWidget)
            cellType = it
            mutation = mutation?.copy(cellType = it, color = getCellColor(it))
            colorOfCellTo = getCellColor(it)
            colorPicker.color = colorOfCellTo
            mutableCircleWidget.setCircleColor(colorOfCellTo)
            setupUI(scrollContentTable)
        }.also { scrollContentTable.add(it).align(Align.left).size(200f * density, 30f * density).padBottom(15f * density).row() }

        if (cellType.isNeural()) {
            neuron(
                action = mutation ?: Action(
                    funActivation = getActivationFuncType()?.toInt(),
                    a = getA(),
                    b = getB(),
                    c = getC(),
                    isSum = getIsSum()
                ),
                game = game,
                bundle = bundle,
                onFuncChange = {
                    if (mutation == null) mutation = Action()
                    mutation = mutation?.copy(funActivation = it)
                },
                onAChange = {
                    if (mutation == null) mutation = Action()
                    mutation = mutation?.copy(a = it)
                },
                onBChange = {
                    if (mutation == null) mutation = Action()
                    mutation = mutation?.copy(b = it)
                },
                onCChange = {
                    if (mutation == null) mutation = Action()
                    mutation = mutation?.copy(c = it)
                },
                onIsSumChange = {
                    if (mutation == null) mutation = Action()
                    mutation = mutation?.copy(isSum = it)
                }
            ).also { scrollContentTable.add(it).align(Align.left).padBottom(10f * density).row() }
        }

        if (cellType.isDirected()) {
            val angle = atan2(
                cellReplay.getAngleSin(startCurrentStageTick,clickedIndex),
                cellReplay.getAngleCos(startCurrentStageTick,clickedIndex)
            )
            val baseParentAngle = getBaseAngleFromParent()

            angleDirected(
                action = mutation ?: Action(
                    angleDirected = angle - baseParentAngle
                ),
                scrollPane = scrollPane,
                game = game,
                bundle = bundle
            ) { angle ->
                if (mutation == null) mutation = Action()
                mutation = mutation?.copy(angleDirected = angle)
                mutableCircleWidget.setAngle(baseParentAngle + angle)
            }.also { scrollContentTable.add(it).width(200f * density).row() }
        }
        if (cellType.isEye()) {
            eye(
                action = mutation ?: Action(
                    lengthDirected = getVisibilityRange(startCurrentStageTick),
                    colorRecognition = getColorDifferentiation(startCurrentStageTick)?.toInt()
                ),
                scrollPane = scrollPane,
                game = game,
                bundle = bundle,
                onDistanceChange = {
                    if (mutation == null) mutation = Action()
                    mutation = mutation?.copy(lengthDirected = it)
                },
                onColorChange = {
                    if (mutation == null) mutation = Action()
                    mutation = mutation?.copy(colorRecognition = it)
                },
            ).also { scrollContentTable.add(it).row()}
        }

        actionButton(bundle.get("button.mutate"), game) {
            if (clickedCell.mutate.hashCode() != mutation.hashCode() && clickedCell.mutate != mutation) {
                mutation?.let { onMutate.invoke(it) }
            }
            fadeOut()
        }.also { scrollContentTable.add(it).size(200f * density, 35f * density).row() }

        pack()
        centerWindow()  // центрируем по экрану // center on the screen
    }

    private fun setupMutation(fromCellType: Int, toCellType: Int, mutableCircleWidget: CircleWidget) {
        when {
            fromCellType.isDirected() && !toCellType.isDirected() -> {
                mutation = mutation?.copy(angleDirected = null)
                mutableCircleWidget.setAngle(null)
            }
            !fromCellType.isDirected() && toCellType.isDirected() -> {
                mutation = mutation?.copy(angleDirected = 0f)
                mutableCircleWidget.setAngle(getBaseAngleFromParent())
            }
        }

        when {
            fromCellType.isNeural() && !toCellType.isNeural() -> {
                mutation = mutation?.copy(
                    funActivation = null,
                    a = null,
                    b = null,
                    c = null,
                    isSum = null
                )
            }
            !fromCellType.isNeural() && toCellType.isNeural() -> {
                mutation = mutation?.copy(
                    funActivation = 0,
                    a = 1f,
                    b = 0f,
                    c = 0f,
                    isSum = true
                )
            }
        }

        when {
            fromCellType.isEye() && !toCellType.isEye() -> {
                mutation = mutation?.copy(
                    colorRecognition = null,
                    lengthDirected = null
                )
            }
            !fromCellType.isEye() && toCellType.isEye() -> {
                mutation = mutation?.copy(
                    colorRecognition = 7,
                    lengthDirected = 4.25f
                )
            }
        }
    }
}
