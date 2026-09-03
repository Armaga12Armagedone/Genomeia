package io.github.some_example_name.old.systems.node

import com.badlogic.gdx.math.Vector2
import io.github.some_example_name.old.features.levelEditor.nodes.ConditionNode

object ConnectionManager {
    private const val SNAP_RADIUS = 24f
    private const val COND_RADIUS = 64f

    enum class Socket { NEXT, IF, ELSE }
    data class Highlight(val parent: Node, val child: Node, val socket: Socket)

    var highlight: Highlight? = null

    fun onDrag(dragged: Node, nodes: List<Node>) {
        val pair = findCandidate(dragged, nodes)
        highlight = pair

        val (parent, child, socket) = pair ?: return

        val target = when (socket) {
            Socket.NEXT -> Vector2(parent.x + parent.width / 2, parent.y)
            Socket.IF -> (parent as ConditionNode).slotDropTarget(Socket.IF, child.nodeWidth)
            Socket.ELSE -> (parent as ConditionNode).slotDropTarget(Socket.ELSE, child.nodeWidth)
        }
        child.moveSubtree(target.x - child.nodeWidth / 2 - child.x, target.y - child.nodeHeight - child.y)
    }

    fun onDragArgument(dragged: Node, nodes: List<Node>) {
        val pair = findCondition(dragged, nodes)
        highlight = pair?.let { (parent, child) -> Highlight(parent, child, Socket.NEXT) }

        val (parent, child) = pair ?: return
        val condition = parent as ConditionNode

        child.moveSubtree(
            condition.argumentSocket.x - child.width / 2 - child.x,
            condition.argumentSocket.y - child.height - child.y
        )
        child.toFront()
    }

    fun onDrop(dragged: Node, nodes: List<Node>) {
        val (parent, child, socket) = highlight ?: return

        //Защита: event/final/уже-подключённые/дескенданты не могут стать дочерними ветками
        if (!child.nodeAction.nodeData.argumentNode && !child.canConnectTo(parent)) {
            highlight = null
            return
        }

        if (child.nodeAction.nodeData.argumentNode) {
            parent.nodeAction.nodeData.arguments["arg"] = child.nodeAction
        }

        when (socket) {
            Socket.NEXT -> {
                parent.childNodes.add(child)
                child.parentNode = parent
            }
            Socket.IF -> {
                val c = parent as ConditionNode
                c.ifNodes.add(child)
                child.parentNode = parent
            }
            Socket.ELSE -> {
                val c = parent as ConditionNode
                c.elseNodes.add(child)
                child.parentNode = parent
            }
        }
        //(parent as? ConditionNode)?.refresh() на этот баг я потратил много времени!
        //println("refreshing")
        parent.refresh()

        highlight = null
    }

    private fun findCandidate(dragged: Node, nodes: List<Node>): Highlight? {
        var best: Highlight? = null
        var bestDist = SNAP_RADIUS
        //Кандидат от полостей condition; если dragged попал в полость — он приоритетнее NEXT
        var slotHit: Highlight? = null

        for (other in nodes) {
            if (other === dragged || other === dragged.ignoreParent) continue
            if (dragged.nodeAction?.nodeData?.finalNode == false && other.canConnectTo(dragged) && !other.nodeAction.nodeData.argumentNode) {
                val d = dragged.outputSocket.dst(other.inputSocket)
                if (d < bestDist) {
                    bestDist = d
                    best = Highlight(other, dragged, Socket.NEXT)
                }
            }
            if (other.nodeAction?.nodeData?.finalNode == false && dragged.canConnectTo(other)  && !dragged.nodeAction.nodeData.argumentNode) {
                val d = dragged.inputSocket.dst(other.outputSocket)
                if (d < bestDist) {
                    bestDist = d
                    best = Highlight(other, dragged, Socket.NEXT)
                }
            }

            //Пристыковка к веткам condition: if/else сокетам
            if (other is ConditionNode) {
                if (dragged.canConnectTo(other)) {
                    val dIf = dragged.inputSocket.dst(other.ifSocket)
                    if (dIf < bestDist) {
                        bestDist = dIf
                        best = Highlight(other, dragged, Socket.IF)
                    }
                    val dElse = dragged.inputSocket.dst(other.elseSocket)
                    if (dElse < bestDist) {
                        bestDist = dElse
                        best = Highlight(other, dragged, Socket.ELSE)
                    }
                    //Если dragged физически находится внутри полости condition — прикрепляем к полости
                    //Внутри canConnectTo: event/final/с-родителем ноды не могут попасть в ветки condition
                    val zone = other.zoneFor(dragged.inputSocket)
                    if (zone != null) slotHit = Highlight(other, dragged, zone)
                }
            }
        }

        //Попадание в полость condition важнее цепочки NEXT к соседям
        if (slotHit != null) return slotHit
        return best
    }

    private fun findCondition(dragged: Node, nodes: List<Node>): Pair<Node, Node>? {
        if (!dragged.nodeAction.nodeData.argumentNode) return null
        var best: Pair<Node, Node>? = null
        var bestDist = COND_RADIUS

        for (other in nodes.filterIsInstance<ConditionNode>()) {
            if (other === dragged || other === dragged.ignoreParent || other.previewNode) continue
            if (other.nodeAction.nodeData.arguments.isEmpty()) {
                val d = dragged.outputSocket.dst(other.argumentSocket)

                if (d < bestDist) {
                    bestDist = d
                    best = other to dragged
                }
            }
        }

        return best
    }
}
