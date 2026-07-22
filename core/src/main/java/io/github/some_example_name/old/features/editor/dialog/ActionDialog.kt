package io.github.some_example_name.old.features.editor.dialog

import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.editor.di.DIGenomeEditorContainer.cellsTypeNames
import io.github.some_example_name.old.editor.entities.EditorCell
import io.github.some_example_name.old.editor.system.ActionDialogSystem
import io.github.some_example_name.old.editor.system.isDirected
import io.github.some_example_name.old.features.editor.colorPickerDialog
import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.physics.CollisionManager.Companion.PARTICLE_MAX_RADIUS
import io.github.some_example_name.old.core.ui.dp
import io.github.some_example_name.old.core.ui.globalVisTable
import io.github.some_example_name.old.core.ui.h
import io.github.some_example_name.old.core.ui.visLabel
import io.github.some_example_name.old.core.ui.visSelectBox
import io.github.some_example_name.old.core.ui.visSlider
import io.github.some_example_name.old.core.ui.visTable
import io.github.some_example_name.old.core.ui.visTextButton
import io.github.some_example_name.old.core.ui.w
import io.github.some_example_name.old.core.ui.setupTitleSize
import kotlin.math.round

enum class ActionDialogType {
    DIVIDE,
    MUTATION,
    CHANGE_REMOVE
}

class ActionDialog(
    val clickedCell: EditorCell,
    val actionDialogType: ActionDialogType,
    newDividedCellPosition: Pair<Float, Float>? = null,
    val onDivide: ((Action) -> Unit)? = null,
    val onChange: ((Action) -> Unit)? = null,
    val onRemove: (() -> Unit)? = null,
    val onMutate: ((Action) -> Unit)? = null,
) : VisDialog("${bundle.get("button.cellId")} ${clickedCell.id}") {

    val actionSystem = ActionDialogSystem(actionDialogType, newDividedCellPosition, clickedCell)

    init {
        setupTitleSize(game)

        isModal = true
        isMovable = true
        val dialog = this

        val rootTable = globalVisTable {
            compose(dialog)
        }

        contentTable.add(rootTable)
            .maxWidth(0.9f * w)
            .maxHeight(0.9f * h)

        closeOnEscape()

        pack()
        centerWindow()
    }

    fun VisTable.compose(dialog: ActionDialog) {
        var dynamicContent: VisTable? = null
        var circleWidget: CircleWidget? = null

        //Выбор типа клетки
        visSelectBox(
            items = cellsTypeNames,
            selectedIndex = actionSystem.cellType,
            onChange = { _, toCellType ->
                val fromCellType = actionSystem.cellType
                actionSystem.setUpDefaultActionData(fromCellType, toCellType)

                //Настройка угла и цвета в зависимости от типа клетки
                if (fromCellType.isDirected() && !toCellType.isDirected()) {
                    circleWidget?.setAngle(null)
                } else if (!fromCellType.isDirected() && toCellType.isDirected()) {
                    circleWidget?.setAngle(actionSystem.angleParent)
                }
                circleWidget?.setCircleColor(actionSystem.getCellColor())

                //Перерисовка UI
                dynamicContent?.clearChildren()
                dynamicContent?.composeDynamic(actionSystem) { circleWidget }
                dynamicContent?.invalidateHierarchy()
                dialog.pack()
                dialog.centerWindow()
            }
        ) { left() }
        row()

        dynamicContent = visTable { }
        dynamicContent.composeDynamic(actionSystem) { circleWidget }
        row()

        if (actionDialogType == ActionDialogType.DIVIDE || actionDialogType == ActionDialogType.CHANGE_REMOVE) {
            //Настройка радиуса клетки при делении
            val defaultRadius = actionSystem.action.radius ?: PARTICLE_MAX_RADIUS
            val radiusLabel = visLabel("Radius: $defaultRadius", font = game.extraLargeFont)
            row()
            visSlider(
                0.2f, 0.5f, 0.01f, value = defaultRadius,
                onValueChange = {
                    val radius = round(it * 100f) / 100f
                    actionSystem.action = actionSystem.action.copy(radius = radius)
                    radiusLabel.setText("Radius: $radius")
                }) { growX().expandX() }
            row()
        }

        visTable {
            if (actionDialogType == ActionDialogType.MUTATION) {
                //Как сейчас выглядит клетка в текущем тике
                val cellType = clickedCell.actual?.cellType ?: throw Exception("actual cellType can't be null")
                visCircleWidget(
                    initialColor = clickedCell.actual.color ?: throw Exception("color is null"),
                    initialDirectedAngle = if (cellType.isDirected()) {
                        actionSystem.angleParent + (clickedCell.angleDirected ?: 0f)
                    } else null
                )

                visLabel("->", font = game.extraLargeFont)
            }

            //Как будет вылгядеть клетка после создания при divide
            circleWidget = visCircleWidget(
                initialColor = actionSystem.action.color ?: clickedCell.actual?.color ?: throw Exception("color is null"),
                initialDirectedAngle = if (actionSystem.isDirected()) actionSystem.angleParent + actionSystem.angleDirected else null
            )
        }
        row()

        if (actionDialogType == ActionDialogType.MUTATION) {
            //Текст списка изменений в следствии mutation
            visLabel(text = actionSystem.makeMutateList(), font = game.largeFont) { left().padLeft(8.dp()) }
            row()
        }

        visTable {
            //Цвет клетки и кнопки action
            visTextButton(bundle.get("button.chooseCellColorDialog"), onClick = {
                stage.colorPickerDialog(
                    initColor = actionSystem.action.color ?: clickedCell.actual?.color ?: throw Exception("color is null"),
                    onFinished = {
                        actionSystem.action = actionSystem.action.copy(color = it)
                        circleWidget?.setCircleColor(it)
                    }
                )
            })

            visTextButton(
                text = when (actionDialogType) {
                    ActionDialogType.DIVIDE -> bundle.get("button.divide")
                    ActionDialogType.MUTATION -> bundle.get("button.mutate")
                    ActionDialogType.CHANGE_REMOVE -> bundle.get("button.change")
                },
                onClick = {
                    when (actionDialogType) {
                        ActionDialogType.DIVIDE -> { onDivide?.invoke(actionSystem.action) }
                        ActionDialogType.MUTATION -> {
                            if (clickedCell.mutate != actionSystem.action && actionSystem.action != Action()) {
                                println(actionSystem.action)
                                onMutate?.invoke(actionSystem.action)
                            }
                        }
                        ActionDialogType.CHANGE_REMOVE -> {
                            if (clickedCell.divide != actionSystem.action) {
                                onChange?.invoke(actionSystem.action)
                            }
                        }
                    }
                    fadeOut()
                }
            )

            if (actionDialogType == ActionDialogType.CHANGE_REMOVE) {
                visTextButton(
                    text = bundle.get("button.remove"),
                    onClick = {
                        onRemove?.invoke()
                        fadeOut()
                    }
                )
            }
        }
    }

    fun VisTable.composeDynamic(actionSystem: ActionDialogSystem, getCircleWidgetFrom: () -> CircleWidget?) {
        if (actionSystem.isNeural()) neuralCompose(actionSystem)
        if (actionSystem.isDirected()) angelDirectedCompose(actionSystem, getCircleWidgetFrom)
        if (actionSystem.isPheromone()) pheromoneCompose(actionSystem)
        if (actionSystem.isEye()) eyeCompose(actionSystem)
    }
}
