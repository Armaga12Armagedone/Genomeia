package io.github.some_example_name.old.features.levelEditor.nodes.actionNodes

import io.github.some_example_name.old.systems.node.Context
import io.github.some_example_name.old.systems.node.Node
import io.github.some_example_name.old.systems.node.NodeData

interface ActionNode {
    var nextNode: ActionNode?
    val id: Int

    val nodeData: NodeData

    fun execute(context: Context)
}
