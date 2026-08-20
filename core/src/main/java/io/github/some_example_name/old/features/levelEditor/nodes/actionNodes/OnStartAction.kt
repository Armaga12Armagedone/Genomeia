package io.github.some_example_name.old.features.levelEditor.nodes.actionNodes

import io.github.some_example_name.old.features.levelEditor.nodes.dataNodes.OnStartUpData
import io.github.some_example_name.old.systems.node.Context
import io.github.some_example_name.old.systems.node.Node
import io.github.some_example_name.old.systems.node.NodeData

class OnStartAction: ActionNode {
    override val id = 0
    override var nextNode: Node? = null
    override val nodeData = OnStartUpData()

    override fun execute(context: Context) {

    }
}
