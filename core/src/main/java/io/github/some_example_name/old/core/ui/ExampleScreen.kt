package io.github.some_example_name.old.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.ScreenViewport

class ExampleScreen : ScreenAdapter() {

    private lateinit var stage: Stage

    override fun show() {
        stage = Stage(ScreenViewport())
        Gdx.input.inputProcessor = stage

        val root = globalVisTable {
            setFillParent(true)
            defaults().padLeft(28.dp())

            visScrollPane({ expand().fill() }) {

                // === ЗАГОЛОВОК ===
                visLabel("GENOMEIA") {
                    center()
                    padBottom(h * 0.018f)
                }

                row()

                visLabel("Выберите сложность:")

                row()

                visSelectBox(
                    items = arrayOf("Лёгкая", "Средняя", "Сложная", "Хардкор"),
                    selectedIndex = 1,
                    onChange = { selected, _ ->
                        Gdx.app.log("VisCompose", "Выбрана сложность: $selected")
                    }
                )

                row()

                val inventory = listOf("Лёгкая", "Средняя", "Сложная", "Хардкор")
                forEach(inventory) { item ->
                    visLabel(item)
                    row()
                }

                visTable(
                    cellInit = { expandX().fillX() },
                    backgroundColor = Color(0.82f, 0.8f, 0.18f, 1f)
                ) {
                    visTable({ expandX().fillX() }) {
                        visTable {
                            visLabel("Заголовок") {
                                padRight(38.dp())
                            }
                        }

                        visTable({ expandX().fillX() }) {
                            forEach(listOf("Лёгкая", "Средняя", "Сложная", "Хардкор")) { item ->
                                visTextButton(item) { expandX().fillX() }
                            }
                        }
                    }

                    row()

                    // === Нижняя секция на всю ширину ===
                    visTable({ expandX().fillX() }) {
                        visLabel("Вложенная секция") {
                            padRight(38.dp())
                        }
                        visTextButton("Кнопка внутри") { expandX().fillX() }
                    }
                }

                row()

                // === КНОПКА С ОБРАБОТЧИКОМ ===
                visTextButton("Начать игру", onClick = {
                    Gdx.app.log("VisCompose", "Кнопка нажата!")
                    // TODO: переход на другой экран
                }) {
                    expandX().fillX().padTop(20f)
                }

                row()

                visTable({ expandX().fillX() }) {
                    visLabel("Громкость:") {
                        padRight(38.dp())
                    }

                    visSlider(
                        min = 0f,
                        max = 100f,
                        step = 5f,
                        value = 75f,
                        onValueChange = { newVolume ->
                            Gdx.app.log("VisCompose", "Громкость изменена: $newVolume")
                            // Здесь можно обновлять состояние игры / аудио
                        }
                    ) {
                        expandX().fillX()
                    }
                }

            }

            row()

        }

        stage.addActor(root)
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.04f, 0.04f, 0.06f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        stage.dispose()
    }
}
