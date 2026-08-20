package io.github.some_example_name.old.features.levelEditor.nodes

import io.github.some_example_name.old.systems.node.Node


object Nodes {
    val nodes = listOf<Node> (
        BaseNode(true),
        OnStartNode(true)
    )
}
