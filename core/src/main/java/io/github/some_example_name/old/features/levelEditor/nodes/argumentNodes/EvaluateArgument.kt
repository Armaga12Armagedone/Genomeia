package io.github.some_example_name.old.features.levelEditor.nodes.argumentNodes

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextField
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.core.ui.makeStyledNP
import io.github.some_example_name.old.core.ui.makeStyledTextField
import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.arguments.evaluateArgumentAction
import io.github.some_example_name.old.game.applyCustomFontMedium
import io.github.some_example_name.old.systems.node.Node
import io.github.some_example_name.old.systems.node.SvgAssets

/**
 * Evaluate-аргумент: два слота, в каждый можно либо кликнуть и ввести текст,
 * либо положить другую арг-ноду (вложенный аргумент).
 */
class EvaluateArgument(val previewNode: Boolean = false): Node(previewNode) {
    override val nodeColor = Color.ORANGE
    override val nodeName = "Eval"
    override val nodeAction = evaluateArgumentAction()

    override val nodeWidth = 128f
    override val nodeHeight = 32f

    override val inputSocket get() = Vector2(x, y)

    override fun getPrefWidth() = nodeWidth
    override fun getPrefHeight() = nodeHeight

    //Слоты: таблица-подложка + вложенная нода (если положили арг-ноду)
    val slotTables = arrayListOf<VisTable>()
    val slotNodes = arrayOfNulls<Node>(2)

    init {
        this.setSize(nodeWidth, nodeHeight)
        this.setBackground(
            svgPath?.let { SvgAssets.drawable(it, nodeWidth.toInt(), nodeHeight.toInt()) }
                ?: makeStyledNP(nodeColor, textures = mutableListOf(), border = Color.BLACK)
        )

        val nameLabel = VisLabel(nodeName)
        game.applyCustomFontMedium(nameLabel)

        val content = VisTable()
        content.add(nameLabel).expand().center().colspan(2).row()
        val slotRow = VisTable()
        repeat(2) { i ->
            val slot = VisTable()
            slot.setSize(56f, 24f)
            slot.setBackground(makeStyledNP(Color.WHITE, textures = mutableListOf(), border = Color.BLACK))
            slotTables += slot
            slot.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (previewNode) return
                    openTextField(i)
                }
            })
            slotRow.add(slot).size(56f, 24f).pad(2f)
        }
        content.add(slotRow).colspan(2)
        this.add(content).expand().fill()
    }

    /** Клик по слоту: подставляет TextField и сохраняет текст в действие. */
    fun openTextField(i: Int) {
        val slot = slotTables[i]
        val arg = nodeAction
        //Если в слоте уже лежит вложенная нода — убираем её
        slotNodes[i]?.let { removeSlotNode(it) }

        slot.clear()
        val tf = makeStyledTextField(game, mutableListOf())
        tf.setSize(56f, 22f)
        when (val cur = if (i == 0) arg.slot1 else arg.slot2) {
            is String -> tf.text = cur
            else -> {}
        }
        tf.addListener(object : ChangeListener() {
            override fun changed(event: ChangeListener.ChangeEvent, actor: Actor) {
                if (i == 0) arg.slot1 = tf.text else arg.slot2 = tf.text
            }
        })
        slot.add(tf).grow()
    }

    /** Прямоугольник i-го слота (stage-координаты) */
    fun slotRect(i: Int): Rectangle {
        val slot = slotTables.getOrNull(i) ?: return Rectangle()
        validate()
        slot.validate()
        val bl = localToStageCoordinates(Vector2(slot.x, slot.y))
        val tr = localToStageCoordinates(Vector2(slot.x + slot.width, slot.y + slot.height))
        return Rectangle(bl.x, bl.y, tr.x - bl.x, tr.y - bl.y)
    }

    /** Положить вложенную арг-ноду в слот. */
    fun setSlotNode(i: Int, n: Node) {
        val arg = nodeAction
        if (i == 0) arg.slot1 = n.nodeAction else arg.slot2 = n.nodeAction
        slotNodes[i] = n
        n.parentNode = this
        refresh()
        n.toFront()
        n.parentNode = this
    }

    /** Убрать вложенную ноду из слота (при откреплении/замене). */
    fun removeSlotNode(n: Node?) {
        if (n == null) return
        for (i in slotNodes.indices) {
            if (slotNodes[i] === n) {
                slotNodes[i] = null
                val arg = nodeAction
                if (i == 0) arg.slot1 = null else arg.slot2 = null
                refresh()
            }
        }
    }

    override fun refresh() {
        super.refresh()
        //Позиционируем вложенные ноды поверх своих слотов (stage-координаты)
        for (i in slotTables.indices) {
            val n = slotNodes[i] ?: continue
            val r = slotRect(i)
            n.setPosition(r.x + (r.width - n.nodeWidth) / 2, r.y + (r.height - n.nodeHeight) / 2)
            n.toFront()
        }
    }

    override fun moveSubtree(dx: Float, dy: Float) {
        moveBy(dx, dy)
        slotNodes.forEach { it?.moveSubtree(dx, dy) }
    }
}
