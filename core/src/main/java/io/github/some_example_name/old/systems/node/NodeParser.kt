package io.github.some_example_name.old.systems.node

import io.github.some_example_name.old.features.levelEditor.nodes.ConditionNode
import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.ActionNode

class NodeParser {
    val events = mutableMapOf<ActionNode, MutableList<ActionNode>>() //по идее он хранит каждый event и у каждого event свой список нодов прикрепленных к ниму.

    fun parse(nodes: List<Node>): MutableMap<ActionNode, MutableList<ActionNode>> {
        for (node in nodes.filter { it.nodeAction.nodeData.eventNode }) {
            val nodesList = mutableListOf<ActionNode>()
            //Проставляем nextNode по основной NEXT-цепочке + веткам condition (домино-модель).
            //В nodesList собираются action основной цепочки (для обратной совместимости/дебага).
            linkChain(node, nodesList, null)
            events[node.nodeAction] = nodesList
        }
        return events
    }

    private fun linkChain(start: Node, list: MutableList<ActionNode>, after: ActionNode?) {
        var current: Node = start
        var prev: ActionNode? = null
        while (true) {
            val next = current.childNodes.firstOrNull() ?: break
            val action = next.nodeAction
            if (action.nodeData.eventNode) { current = next; continue }
            if (prev != null) prev.nextNode = action
            prev = action
            list.add(action)

            if (next is ConditionNode) {
                for (b in next.ifNodes) linkChain(b, mutableListOf(), null)
                for (b in next.elseNodes) linkChain(b, mutableListOf(), null)
            }
            current = next
        }
        if (prev != null) prev.nextNode = after
    }
}

