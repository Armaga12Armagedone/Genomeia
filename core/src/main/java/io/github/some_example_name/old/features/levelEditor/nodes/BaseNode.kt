package io.github.some_example_name.old.features.levelEditor.nodes

import com.badlogic.gdx.graphics.Color
import io.github.some_example_name.old.systems.node.Node

class BaseNode(val previewNode: Boolean = false): Node(previewNode) {
    override val nodeColor = Color.RED
    override val nodeName = "Base node"

    init {
        super.init()
    }

//    override fun draw() {
//        super.draw()
//    }
}
