package io.github.some_example_name.old.features.levelEditor.nodes

import com.badlogic.gdx.graphics.Color
import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.OnStartAction
import io.github.some_example_name.old.features.levelEditor.nodes.dataNodes.OnStartUpData
import io.github.some_example_name.old.systems.node.Node

class OnStartNode(val previewNode: Boolean = false): Node(previewNode) {
    override val nodeColor = Color.CYAN
    override val nodeName = "OnStart"

    init {
        super.init()
        nodeAction = OnStartAction()
    }

//    override fun draw() {
//        super.draw()
//    }
}
