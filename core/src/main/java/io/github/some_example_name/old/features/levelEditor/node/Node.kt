package io.github.some_example_name.old.features.levelEditor.node

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.core.ui.makeStyledNP
import io.github.some_example_name.old.game.applyCustomFont
import kotlin.collections.mutableListOf


open class Node(val preview: Boolean = false): VisTable() {
    open val nodeName: String = "Base"
    open val nodeColor: Color = Color.RED
    open val nodeWidth = 256f
    open val nodeHeight = 128f
    open val event = true
    open val finalNode = false
    val childNodes = mutableListOf<Node>()

    open fun init() {
        this.setSize(nodeWidth, nodeHeight)
        //this.setBackground(VisUI.getSkin().newDrawable("white", nodeColor));
        this.setBackground(makeStyledNP(Color.RED, textures = mutableListOf(), border = Color.BLACK))

        val nameLabel = VisLabel(nodeName)
        this.top()
        game.applyCustomFont(nameLabel)
        this.add(nameLabel).fillX()

        this.setTouchable(Touchable.enabled)
        if (!preview) {
            this.addListener(object : DragListener() {
                public override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    val stageCoords = Vector2(Gdx.input.getX().toFloat(), Gdx.input.getY().toFloat())

                    stage.screenToStageCoordinates(stageCoords)
                    setPosition(stageCoords.x - nodeWidth / 2, stageCoords.y - nodeHeight / 2)
                }
            })
        }
    }
}
