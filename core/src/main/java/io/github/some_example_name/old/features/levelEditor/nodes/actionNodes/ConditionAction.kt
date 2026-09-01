package io.github.some_example_name.old.features.levelEditor.nodes.actionNodes

import io.github.some_example_name.old.systems.node.Context
import io.github.some_example_name.old.systems.node.NodeData

class ConditionAction: ActionNode {
    override val id = 3
    override var nextNode: ActionNode? = null //то что снаружи после condition
    override val nodeData = NodeData(funcNode = true)

    val ifChain = mutableListOf<ActionNode>() //цепочка if (условие выполняется)
    val elseChain = mutableListOf<ActionNode>() //цепочка else (условие не выполняется)

    override fun execute(context: Context) {
        val arg = nodeData.arguments["arg"] as? BaseArgumentAction
        val branch = if (arg?.value == true) ifChain else elseChain
        for (n in branch) n.execute(context)
    }
}