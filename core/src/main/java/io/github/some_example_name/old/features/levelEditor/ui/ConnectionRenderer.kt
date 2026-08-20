package io.github.some_example_name.old.features.levelEditor.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.scenes.scene2d.Actor
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
            val cx = node.x + node.width / 2
            for (child in node.childNodes) {
                shapes.setColor(Color.WHITE)
                shapes.rectLine(cx, node.y, child.x + child.width / 2, child.y + child.height, 3f)
            }
            val inFree = !node.event && node.parentNode == null
            val outFree = !node.finalNode && node.childNodes.isEmpty()
            shapes.setColor(if (inFree) Color.GREEN else Color.GRAY)
            shapes.circle(cx, node.y + node.height, 5f)
            shapes.setColor(if (outFree) Color.GREEN else Color.GRAY)
            shapes.circle(cx, node.y, 5f)
        }
        if (hl != null) {
            shapes.setColor(Color.YELLOW)
            shapes.circle(hl.first.outputSocket.x, hl.first.outputSocket.y, 8f)
            shapes.circle(hl.second.inputSocket.x, hl.second.inputSocket.y, 8f)
        }
        shapes.end()
        batch.begin()
    }
}
