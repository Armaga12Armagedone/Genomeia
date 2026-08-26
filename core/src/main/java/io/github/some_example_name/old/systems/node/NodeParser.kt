package io.github.some_example_name.old.systems.node

import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.ActionNode

class NodeParser {
    val events = mutableMapOf<ActionNode, MutableList<ActionNode>>() //по идее он хранит каждый event и у каждого event свой список нодов прикрепленных к ниму.

    fun parse(nodes: List<Node>): MutableMap<ActionNode, MutableList<ActionNode>> {
        for (node in nodes.filter { it.nodeAction.nodeData.eventNode }) {
            //events[node.nodeAction] = mutableListOf<ActionNode>()//node.nodeAction.nextNode
            //var nextNode: ActionNode? = node.nodeAction.nextNode
            val nodesList = mutableListOf<ActionNode>()
            var currentNode = node
            while (currentNode.childNodes.isNotEmpty()) {
                currentNode = currentNode.childNodes.first() //ветвление не будет мухаха, пока что :)
                if (currentNode.nodeAction.nodeData.eventNode) { continue }
                nodesList.add(currentNode.nodeAction)
            }

            events[node.nodeAction] = nodesList
        }
        return events
    }

}

