package io.github.some_example_name.old.features.levelEditor.nodes

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.ConditionAction
import io.github.some_example_name.old.features.levelEditor.nodes.argumentNodes.BaseArgument
import io.github.some_example_name.old.systems.node.ConnectionManager
import io.github.some_example_name.old.systems.node.Node
import io.github.some_example_name.old.systems.node.SvgAssets

class ConditionNode(val previewNode: Boolean = false) : Node(previewNode) {
    override val nodeColor = Color.BLUE
    override val nodeName = "If"
    override val nodeAction = ConditionAction()
    override var nodeWidth = ConditionSvg.BASE_WIDTH
    override var nodeHeight = ConditionSvg.height(ConditionSvg.BASE_WIDTH, 0f, 0f)

    val ifNodes = mutableListOf<Node>()
    val elseNodes = mutableListOf<Node>()

    //Уникальный ключ текстуры экземпляра: инвалидация не задевает палитру-превью
    private val svgKey = "ConditionNode#" + serial++

    override val inputSocket get() = Vector2(x + nodeWidth / 2, (y + nodeHeight) - 10f)
    override val outputSocket get() = Vector2(x + nodeWidth / 2, y)

    private fun headerH() = 50f * s()
    private fun lipH() = 48f * s()
    private fun s() = nodeWidth / 168f

    // Сокет аргумента — гексагон в шапке
    val argumentSocket get() = localToStageCoordinates(Vector2(81.5f * s(), nodeHeight - 4f * s()))
    // Вход в if-полость = низ шапки
    val ifSocket get() = localToStageCoordinates(Vector2(nodeWidth / 2f, nodeHeight - headerH()))
    // Вход в else-полость = низ губы
    val elseSocket get() = localToStageCoordinates(Vector2(nodeWidth / 2f, nodeHeight - headerH() - ifH() - lipH()))

    private fun ifH(): Float = contentHeight(ifNodes).coerceAtLeast(ConditionSvg.MIN_SLOT)
    private fun elseH(): Float = contentHeight(elseNodes).coerceAtLeast(ConditionSvg.MIN_SLOT)

    private fun contentHeight(nodes: List<Node>): Float {
        if (nodes.isEmpty()) return 0f
        var sum = 0f
        for (n in nodes) sum += n.nodeHeight
        return sum + (nodes.size - 1) * GAP
    }

    init {
        super.init()
    }

    override fun initUI() {
        this.setSize(nodeWidth, nodeHeight)
        this.setBackground(bg())
    }

    override fun moveSubtree(dx: Float, dy: Float) {
        moveBy(dx, dy)
        (childNodes + ifNodes + elseNodes).distinct().forEach { it.moveSubtree(dx, dy) }
    }

    override fun refresh() {
        super.refresh()
        nodeAction.ifChain.clear()
        nodeAction.ifChain.addAll(ifNodes.map { it.nodeAction })
        nodeAction.elseChain.clear()
        nodeAction.elseChain.addAll(elseNodes.map { it.nodeAction })

        val prevHeight = nodeHeight
        nodeHeight = ConditionSvg.height(nodeWidth, ifH(), elseH())
        this.setSize(nodeWidth, nodeHeight)
        SvgAssets.invalidateCondition(svgKey)
        this.setBackground(bg())

        //Растём ВНИЗ: верхний край нода остаётся на месте (scene2d setSize растёт вверх).
        //Смещаем сам нод и его нижнюю цепочку вниз на прирост высоты.
        val delta = nodeHeight - prevHeight
        if (delta != 0f) {
            this.moveBy(0f, -delta)
            childNodes.filterNot { it is BaseArgument }.forEach { it.moveSubtree(0f, -delta) }
        }

        //childNodes.filter { it is BaseArgument }.forEach { it.moveSubtree(0f, 20f) }

        //if-полость: ноды от низа шапки вниз, прижаты к левой стенке
        var cursor = y + nodeHeight - headerH()
        for (n in ifNodes) {
            n.setPosition(contentX(), cursor - n.nodeHeight)
            cursor -= n.nodeHeight + GAP
        }
        //else-полость: ноды от низа губы вниз
        cursor = y + nodeHeight - headerH() - ifH() - lipH()
        for (n in elseNodes) {
            n.setPosition(contentX(), cursor - n.nodeHeight)
            cursor -= n.nodeHeight + GAP
        }
    }

    /** X содержимого — сразу справа от левой стенки полости. */
    private fun contentX() = x + 18f * s()

    /**
     * Точка (stage-координаты) предпросмотра перетаскиваемого нода в полости:
     * прижат к левой стенке, стопкой ПОД уже лежащими нодами полости.
     */
    fun slotDropTarget(zone: ConnectionManager.Socket, childWidth: Float): Vector2 {
        val list = if (zone == ConnectionManager.Socket.IF) ifNodes else elseNodes
        val centerX = contentX() + childWidth / 2f
        val bottom = if (list.isEmpty()) {
            val top = if (zone == ConnectionManager.Socket.IF)
                y + nodeHeight - headerH() else y + nodeHeight - headerH() - ifH() - lipH()
            top
        } else {
            list.last().y
        }
        return Vector2(centerX, bottom)
    }

    fun zoneFor(pt: Vector2): ConnectionManager.Socket? {
        if (pt.x < contentX() || pt.x > x + nodeWidth) return null
        val ifTop = y + nodeHeight - headerH()
        val elseTop = y + nodeHeight - headerH() - ifH() - lipH()
        //IF-полость: вся полоса между низом шапки и губой — ловим и добавление в конец
        //цепочки (точка НИЖЕ текущей высоты полости), а не только текущую высоту.
        if (pt.y <= ifTop && pt.y > elseTop) return ConnectionManager.Socket.IF
        //ELSE-полость: от губы вниз с запасом под добавление в конец цепочки
        if (pt.y <= elseTop && pt.y >= elseTop - elseH() - GROW_MARGIN) return ConnectionManager.Socket.ELSE
        return null
    }

    private fun bg() = SvgAssets.conditionDrawable(
        svgKey, ConditionSvg.SVG, nodeWidth.toInt(), Math.round(ifH()), Math.round(elseH())
    )

    companion object {
        private const val GAP = 8f
        private const val GROW_MARGIN = 240f
        private var serial = 0
    }
}
