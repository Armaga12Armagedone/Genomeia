package io.github.some_example_name.old.features.levelEditor.nodes

import io.github.some_example_name.old.features.levelEditor.node.Node

object Nodes {
    val nodes = listOf<Node> (
        BaseNode(true).apply { init() }
    )
}
