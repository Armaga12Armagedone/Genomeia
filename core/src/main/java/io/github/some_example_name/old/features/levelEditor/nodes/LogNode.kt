package io.github.some_example_name.old.features.levelEditor.nodes

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.ui.makeStyledTextField
import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.LogAction
import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.OnStartAction
import io.github.some_example_name.old.systems.node.Node
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener

class LogNode(val previewNode: Boolean = false): Node(previewNode) {
    override val nodeColor = Color.CYAN
    override val nodeName = "Log"
    override val nodeAction = LogAction()

    init {
        super.init()

        val textField = makeStyledTextField(DIGameGlobalContainer.game,mutableListOf<Texture>())

        textField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeListener.ChangeEvent, actor: Actor) {
                nodeAction.nodeData.arguments["string"] = textField.text
                println(textField.text)
            }
        } )

        this.add(textField).row()
    }
}
