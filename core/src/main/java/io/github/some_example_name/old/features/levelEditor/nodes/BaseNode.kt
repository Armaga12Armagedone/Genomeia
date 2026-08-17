package io.github.some_example_name.old.features.levelEditor.nodes

import com.badlogic.gdx.graphics.Color
import io.github.some_example_name.old.features.levelEditor.node.Node

class BaseNode: Node() {
    override val nodeColor = Color.RED
    override val nodeName = "Base node"

//    override fun draw() {
//        super.draw()
//    }
}
