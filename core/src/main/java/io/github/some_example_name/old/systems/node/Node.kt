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
import io.github.some_example_name.old.features.levelEditor.nodes.ConditionNode
import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.ActionNode
import io.github.some_example_name.old.game.applyCustomFont

open class Node(val preview: Boolean = false) : VisTable() {
    open val nodeName: String = "Base"
    open val nodeColor: Color = Color.RED
    open val nodeWidth = 256f
    open val nodeHeight = 128f
    open val nodeAction: ActionNode = BaseAction()
    open val svgPath: String? = null

    val childNodes = mutableListOf<Node>()
    var parentNode: Node? = null
    var ignoreParent: Node? = null

    // ДОБАВЛЕНО: для корректного восстановления узла в нужную полость ConditionNode при отмене перетаскивания
    var previousConditionZone: ConnectionManager.Socket? = null

    open val inputSocket get() = Vector2(x + nodeWidth / 2, y + nodeHeight)
    open val outputSocket get() = Vector2(x + nodeWidth / 2, y)

    fun canConnectTo(parent: Node): Boolean {
        if (nodeAction?.nodeData?.eventNode == true || parent.nodeAction?.nodeData?.finalNode == true) return false
        if (parent === this || parentNode != null) return false
        return !isDescendant(parent)
    }

    fun connectTo(parent: Node) {
        parent.childNodes.add(this)
        parentNode = parent
    }

    fun disconnectFromParent() {
        val parent = parentNode
        if (parent is ConditionNode) {
            // ДОБАВЛЕНО: запоминаем, откуда именно был удален узел, для возможного отката
            if (parent.ifNodes.remove(this)) {
                previousConditionZone = ConnectionManager.Socket.IF
            } else if (parent.elseNodes.remove(this)) {
                previousConditionZone = ConnectionManager.Socket.ELSE
            }
            parent.refresh()
        }
        parent?.childNodes?.remove(this)
        parent?.nodeAction?.nodeData?.arguments?.entries?.removeAll { it.value === this }
        parentNode = null
    }

    open fun moveSubtree(dx: Float, dy: Float) {
        moveBy(dx, dy)
        childNodes.forEach { it.moveSubtree(dx, dy) }
    }

    private fun isDescendant(node: Node): Boolean =
        childNodes.any { it === node || it.isDescendant(node) }

    open fun init() {
        initUI()
        initLogic()
    }

    open fun initUI() {
        this.setSize(nodeWidth, nodeHeight)
        this.setBackground(
            svgPath?.let { SvgAssets.drawable(it, nodeWidth.toInt(), nodeHeight.toInt()) }
                ?: makeStyledNP(nodeColor, textures = mutableListOf(), border = Color.BLACK)
        )

        val nameLabel = VisLabel(nodeName)
        this.top()
        game.applyCustomFont(nameLabel)
        this.add(nameLabel).fillX()
    }

    open fun refresh() {

    }

    open fun initLogic() {
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
                    //Хватаем нод за верхний край (а не за центр): так легче стыковать цепочки
                    moveSubtree(
                        stageCoords.x - nodeWidth / 2 - this@Node.x,
                        stageCoords.y - nodeHeight - this@Node.y
                    )

                    if (nodeAction.nodeData.argumentNode) {
                        ConnectionManager.onDragArgument(this@Node, stage.actors.filterIsInstance<Node>())
                        return
                    }
                    ConnectionManager.onDrag(this@Node, stage.actors.filterIsInstance<Node>())
                }

                public override fun dragStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    ConnectionManager.onDrop(this@Node, stage.actors.filterIsInstance<Node>())

                    // ИСПРАВЛЕНО: сохраняем значение в локальную val-переменную для безопасного smart cast
                    val currentIgnoreParent = ignoreParent

                    if (parentNode == null && currentIgnoreParent != null) {
                        connectTo(currentIgnoreParent)

                        if (currentIgnoreParent is ConditionNode) {
                            if (previousConditionZone == ConnectionManager.Socket.IF) {
                                currentIgnoreParent.ifNodes.add(this@Node)
                            } else if (previousConditionZone == ConnectionManager.Socket.ELSE) {
                                currentIgnoreParent.elseNodes.add(this@Node)
                            }
                            currentIgnoreParent.refresh()
                        }
                    }

                    ignoreParent = null
                    previousConditionZone = null
                }
            })
        }
    }
}

class BaseAction : ActionNode {
    override val id = 0
    override var nextNode: ActionNode? = null
    override val nodeData = NodeData(funcNode = true)

    override fun execute(context: Context) {
        println("Base Node Executed")
        nextNode?.execute(context)
    }
}
