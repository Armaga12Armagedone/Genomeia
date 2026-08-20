package io.github.some_example_name.old.systems.node

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.core.ui.makeStyledNP
import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.ActionNode
import io.github.some_example_name.old.game.applyCustomFont
import kotlin.collections.mutableListOf


open class Node(val preview: Boolean = false): VisTable() {
    open val nodeName: String = "Base"
    open val nodeColor: Color = Color.RED
    open val nodeWidth = 256f
    open val nodeHeight = 128f
    open var nodeAction: ActionNode? = null
     //executer node это по сути команда, компилятор по идее должен работать так: event-executer-final, при этом executer может быть final. Ну надо посмотреть

    val childNodes = mutableListOf<Node>()
    var parentNode: Node? = null
    var ignoreParent: Node? = null

    val inputSocket get() = Vector2(x + nodeWidth / 2, y + nodeHeight)
    val outputSocket get() = Vector2(x + nodeWidth / 2, y)

    fun canConnectTo(parent: Node): Boolean {
        if (nodeAction?.nodeData?.eventNode == false || parent.nodeAction?.nodeData?.finalNode == false) return false
        if (parent === this || parentNode != null) return false
        return !isDescendant(parent)
    }

    fun connectTo(parent: Node) {
        parent.childNodes.add(this)
        parentNode = parent
    }

    fun disconnectFromParent() {
        parentNode?.childNodes?.remove(this)
        parentNode = null
    }

    fun moveSubtree(dx: Float, dy: Float) {
        moveBy(dx, dy)
        childNodes.forEach { it.moveSubtree(dx, dy) }
    }

    private fun isDescendant(node: Node): Boolean =
        childNodes.any { it === node || it.isDescendant(node) }

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
                public override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    ignoreParent = parentNode
                    disconnectFromParent()
                }

                public override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    val stageCoords = Vector2(Gdx.input.getX().toFloat(), Gdx.input.getY().toFloat())
                    stage.screenToStageCoordinates(stageCoords)
                    moveSubtree(
                        stageCoords.x - nodeWidth / 2 - this@Node.x,
                        stageCoords.y - nodeHeight / 2 - this@Node.y
                    )
                    ConnectionManager.onDrag(this@Node, stage.actors.filterIsInstance<Node>())
                }

                public override fun dragStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    ConnectionManager.onDrop(this@Node, stage.actors.filterIsInstance<Node>())
                    ignoreParent = null
                }
            })
        }
    }
}
