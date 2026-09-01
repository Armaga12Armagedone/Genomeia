package io.github.some_example_name.old.features.levelEditor.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.scenes.scene2d.Actor
import io.github.some_example_name.old.features.levelEditor.nodes.ConditionNode
import io.github.some_example_name.old.systems.node.ConnectionManager
import io.github.some_example_name.old.systems.node.Node

class ConnectionRenderer(private val nodes: List<Node>): Actor() {
    private val shapes = ShapeRenderer()

    override fun draw(batch: Batch, parentAlpha: Float) {
        batch.end()
        shapes.projectionMatrix = batch.projectionMatrix
        shapes.begin(ShapeType.Filled)
        val hl = ConnectionManager.highlight
        for (node in nodes) {
            for (child in node.childNodes) {
                shapes.setColor(Color.WHITE)
                shapes.rectLine(node.outputSocket, child.inputSocket, 3f)
            }
            if (node is ConditionNode) {
                for (child in node.ifNodes) {
                    shapes.setColor(Color.WHITE)
                    shapes.rectLine(node.ifSocket, child.inputSocket, 3f)
                }
                for (child in node.elseNodes) {
                    shapes.setColor(Color.WHITE)
                    shapes.rectLine(node.elseSocket, child.inputSocket, 3f)
                }
            }
            val inFree = !(node?.nodeAction?.nodeData?.eventNode == true) && node.parentNode == null
            val outFree = !(node?.nodeAction?.nodeData?.finalNode == true) && node.childNodes.isEmpty()
            shapes.setColor(if (inFree) Color.GREEN else Color.GRAY)
            shapes.circle(node.inputSocket.x, node.inputSocket.y, 5f)
            shapes.setColor(if (outFree) Color.GREEN else Color.GRAY)
            shapes.circle(node.outputSocket.x, node.outputSocket.y, 5f)

            if (node is ConditionNode) {
                shapes.setColor(if (node.ifNodes.isEmpty()) Color.GREEN else Color.GRAY)
                shapes.circle(node.ifSocket.x, node.ifSocket.y, 5f)
                shapes.setColor(if (node.elseNodes.isEmpty()) Color.GREEN else Color.GRAY)
                shapes.circle(node.elseSocket.x, node.elseSocket.y, 5f)
            }
        }
        if (hl != null) {
            val (parent, child, socket) = hl
            val target = when (socket) {
                ConnectionManager.Socket.NEXT -> parent.outputSocket
                ConnectionManager.Socket.IF -> (parent as ConditionNode).ifSocket
                ConnectionManager.Socket.ELSE -> (parent as ConditionNode).elseSocket
            }
            shapes.setColor(Color.YELLOW)
            shapes.circle(target.x, target.y, 8f)
            shapes.circle(child.inputSocket.x, child.inputSocket.y, 8f)
        }
        shapes.end()
        batch.begin()
    }
}
