package io.github.some_example_name.old.features.levelEditor.nodes.actionNodes

import io.github.some_example_name.old.systems.genomics.genome.Action
import io.github.some_example_name.old.systems.node.Context
import io.github.some_example_name.old.systems.node.NodeData

class BaseArgumentAction: ActionNode {
    override val id = 4
    override var nextNode: ActionNode? = null
    override val nodeData = NodeData(argumentNode = true)

    var value: Boolean? = true

    override fun execute(context: Context) {

    }
}
