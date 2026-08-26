package io.github.some_example_name.old.features.levelEditor.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisTable
import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.old.core.ui.density
import io.github.some_example_name.old.core.ui.makeStyledButton
import io.github.some_example_name.old.systems.node.ConnectionManager
import io.github.some_example_name.old.systems.node.Node
import io.github.some_example_name.old.features.levelEditor.nodes.Nodes
import io.github.some_example_name.old.game.MyGame
import io.github.some_example_name.old.systems.node.NodeExecuter
import it.unimi.dsi.fastutil.ints.IntLists

class LevelEditorScreen: Screen {

    private val stage: Stage = Stage()
    private val camera = OrthographicCamera().apply {
        setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
    }

    private val batch = SpriteBatch()

    //val node = BaseNode(false).apply { this.init() }
    val nodes = mutableListOf<Node>()
    val nodeExecuter = NodeExecuter()

    init {
        setupUI()
    }

    fun setupUI() {
        val table = VisTable()
        table.setFillParent(true)

        stage.addActor(ConnectionRenderer(nodes))

        val sideMenu = VisTable()
        sideMenu.setBackground(VisUI.getSkin().newDrawable("white", Color.GRAY))

        for (nodePrototype in Nodes.nodes) {
            nodePrototype.addListener(object : DragListener() {
                private var draggedNode: Node? = null
                private var lastX = 0f
                private var lastY = 0f

                override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int){
                    val newInstance = try {
                        nodePrototype.javaClass.getDeclaredConstructor(Boolean::class.java) //создани такой же ноды
                            .newInstance(false)
                    } catch (e: Exception) {
                        nodePrototype::class.java.newInstance().apply {
                        }
                    }
                    newInstance.initLogic()

                    stage.addActor(newInstance)

                    nodes.add(newInstance)

                    val stageCoords = Vector2(Gdx.input.getX().toFloat(), Gdx.input.getY().toFloat())
                    stage.screenToStageCoordinates(stageCoords)
                    newInstance.setPosition(
                        stageCoords.x - newInstance.nodeWidth / 2,
                        stageCoords.y - newInstance.nodeHeight / 2
                    )
                    lastX = x
                    lastY = y
                    draggedNode = newInstance
                }

                override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    val node = draggedNode ?: return
                    node.moveSubtree(x - lastX, y - lastY)
                    lastX = x
                    lastY = y
                    ConnectionManager.onDrag(node, nodes)
                }

                override fun dragStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    val node = draggedNode ?: return
                    ConnectionManager.onDrop(node, nodes)
                    draggedNode = null
                }
            })
            sideMenu.add(nodePrototype).row()
        }

        table.add(sideMenu)
            .height(Gdx.graphics.height.toFloat())
            .width(384 * density)
            .expand()
            .right()

        val runButton = makeStyledButton("Run", game = DIGameGlobalContainer.game, textures = mutableListOf<Texture>()).apply {
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    super.clicked(event, x, y)
                    nodeExecuter.run(nodes)
                }
            })
        }

        table.add(runButton).top().left().row()

        stage.addActor(table)
    }

    override fun dispose() {
        stage.dispose()
    }

    override fun hide() {
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.10f, 0.12f, 0.14f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        camera.update()

//        batch.begin()
//        //node.draw(batch = batch)
//        batch.end()

        stage.act(delta)
        stage.draw()
    }

    override fun pause() {
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)

        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
    }

    override fun show() {
        Gdx.input.setInputProcessor(stage)

        //stage.addActor(node)
        //node.setPosition(256f, 256f)
    }

    override fun resume() {
    }
}
