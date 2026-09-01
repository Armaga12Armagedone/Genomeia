package io.github.some_example_name.old.features.levelEditor.nodes.argumentNodes

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.compression.lzma.Base
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.core.ui.makeStyledNP
import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.BaseArgumentAction
import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.ConditionAction
import io.github.some_example_name.old.game.applyCustomFont
import io.github.some_example_name.old.game.applyCustomFontMedium
import io.github.some_example_name.old.systems.node.BaseAction
import io.github.some_example_name.old.systems.node.Node
import io.github.some_example_name.old.systems.node.SvgAssets

class BaseArgument(val previewNode: Boolean = false): Node(previewNode) {
    override val nodeColor = Color.ORANGE
    override val nodeName = "Arg"
    override val nodeAction = BaseArgumentAction()

    override val nodeWidth = 64f
    override val nodeHeight = 32f

    override val inputSocket get() = Vector2(x, y)

    override fun getPrefWidth() = nodeWidth
    override fun getPrefHeight() = nodeHeight

    //UI
    val condition = VisTable()

    init {
        //super.init() это нудно что бы сделать нормального размера шрифт, хз как сделать по другому :(
        this.setSize(nodeWidth, nodeHeight)
        this.setBackground(
            svgPath?.let { SvgAssets.drawable(it, nodeWidth.toInt(), nodeHeight.toInt()) }
                ?: makeStyledNP(nodeColor, textures = mutableListOf(), border = Color.BLACK)
        )

        val nameLabel = VisLabel(nodeName)
        game.applyCustomFontMedium(nameLabel)
        //Центрируем текст по вертикали: в маленькой скруглённой ноде top-выравнивание
        //прижимает лейбл к скруглённому верху, из-за чего текст "съезжает вниз"
        this.add(nameLabel).expand().center()
    }
}
