package io.github.some_example_name.old.features.levelEditor.nodes.actionNodes

import io.github.some_example_name.old.systems.node.Context
import io.github.some_example_name.old.systems.node.NodeData

class LogAction: ActionNode {
    override val id = 2
    override var nextNode: ActionNode? = null
    override val nodeData = NodeData(funcNode = true, arguments = mutableMapOf<Any, Any>())

    override fun execute(context: Context) {
        println(nodeData.arguments["string"])
        nextNode?.execute(context)
    }
}
