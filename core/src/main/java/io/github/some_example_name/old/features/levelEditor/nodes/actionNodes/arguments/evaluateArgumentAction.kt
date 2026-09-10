package io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.arguments

import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.ActionNode
import io.github.some_example_name.old.systems.node.Context
import io.github.some_example_name.old.systems.node.NodeData

class evaluateArgumentAction: ActionNode {
    override val id = 5
    override var nextNode: ActionNode? = null
    override val nodeData = NodeData(argumentNode = true)

    var value: Boolean? = true

    //Слоты внутри evaluate-аргумента: каждый хранит либо текст (String), либо вложенный аргумент (ActionNode)
    var slot1: Any? = null
    var slot2: Any? = null

    override fun execute(context: Context) {

    }
}
