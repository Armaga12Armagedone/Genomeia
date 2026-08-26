package io.github.some_example_name.old.features.levelEditor.nodes

import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.ActionNode
import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.OnStartAction
import io.github.some_example_name.old.systems.node.Node
import io.github.some_example_name.old.systems.node.NodeData


object Nodes {
    val nodes = listOf<Node> (
        BaseNode(true),
        OnStartNode(true),
        LogNode(true)
    )
//
//    val dataNodes = mapOf<ActionNode, NodeData>(
//        OnStartAction() to NodeData(eventNode = true)
//    )
}
