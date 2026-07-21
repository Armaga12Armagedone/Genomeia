package io.github.some_example_name.old.editor.ui

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import com.kotcrab.vis.ui.widget.VisTextField
import io.github.some_example_name.old.core.ApiClient
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.ui.dialogs.setupTitleSize
import io.github.some_example_name.old.systems.genomics.genome.Genome
import io.github.some_example_name.old.systems.genomics.genome.saveGenome
import io.github.some_example_name.old.ui.core.dp
import io.github.some_example_name.old.ui.core.globalVisTable
import io.github.some_example_name.old.ui.core.visLabel
import io.github.some_example_name.old.ui.core.visTable
import io.github.some_example_name.old.ui.core.visTextButton
import io.github.some_example_name.old.ui.core.visTextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

//TODO перейти на compose стиль
class SaveGenomeDialog(
    val genome: Genome,
    val onSaveAndTest: (String) -> Unit,
    val onGoMenu: () -> Unit,
    val isGoToMenu: Boolean
) : VisDialog(bundle.get("button.saveGenome")) {

    private val textures = mutableListOf<Texture>()

    fun VisTable.compose() {
        var genomeNameField: VisTextField? = null
        var saveToFileAndTestButton: VisTextButton? = null
        var saveToFileButton: VisTextButton? = null
        var exportButton: VisTextButton? = null

        visTable ( {growX().expandX()}) {
            visLabel(bundle.get("button.name")) {
                pad(8.dp())
            }

            genomeNameField = visTextField(text = genome.name, onTextChange = { text ->
                val isDisabled = text.isEmpty()
                saveToFileButton?.isDisabled = isDisabled
                saveToFileAndTestButton?.isDisabled = isDisabled
                exportButton?.isDisabled = isDisabled
            }) {
                growX().expandX()
            }
        }
        row()

        visTable ( {growX().expandX()}) {
            saveToFileAndTestButton = visTextButton(bundle.get("button.saveAndTest"), onClick = {
                val name = genomeNameField?.text ?: throw Exception("genomeNameField is null")
                saveGenome(
                    genome.copy(name = name),
                    name
                )
                onSaveAndTest.invoke("${genomeNameField.text}")
                fadeOut()
            })
        }
        row()

        visTable {
            saveToFileButton = visTextButton(bundle.get("button.saveToFile"), onClick = {
                val name = genomeNameField?.text ?: throw Exception("genomeNameField is null")
                saveGenome(
                    genome.copy(name = name),
                    name
                )
            })
            if (isGoToMenu) {
                visTextButton(bundle.get("button.menu"), onClick = {
                    onGoMenu.invoke()
                }) { center() }
            }
        }
        row()

        visTable {
            if (Gdx.app.type == Application.ApplicationType.Android) {
                exportButton = visTextButton(bundle.get("button.saveAndExport"), onClick = {
                    val name = genomeNameField?.text ?: throw Exception("genomeNameField is null")
                    saveGenome(
                        genome.copy(name = name),
                        name
                    )
                    game.multiPlatformFileProvider.exportGenome("genomes/$name.genome")
                }) { center() }
            }

            visTextButton("Share with everyone", onClick = {
                val name = genomeNameField?.text ?: throw Exception("genomeNameField is null")
                saveGenome(
                    genome.copy(name = name),
                    name
                )

                // Загружаем на сервер
                val genomeFile = Gdx.files.local("genomes/$name.genome")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ApiClient.uploadFile(genomeFile.file())   // .file() → java.io.File
                        Gdx.app.postRunnable {
                            // Здесь можно показать уведомление "Успешно отправлено"
                            println("Genome \"$name\" успешно загружен на сервер")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Gdx.app.postRunnable {
                            // Здесь можно показать диалог с ошибкой
                            println("Ошибка загрузки: ${e.message}")
                        }
                    }
                }
            }) { center() }
        }
        row()



        val isDisabled = genomeNameField?.text?.isEmpty() ?: false
        saveToFileButton?.isDisabled = isDisabled
        saveToFileAndTestButton?.isDisabled = isDisabled
        exportButton?.isDisabled = isDisabled
    }

    init {
        setupTitleSize(game)

        isModal = true
        isMovable = true

        val rootTable = globalVisTable { compose() }

        contentTable.add(rootTable)

        closeOnEscape()

        pack()
        centerWindow()
    }

    override fun hide(action: Action?) {
        super.hide(action)
        addAction(
            Actions.sequence(
            Actions.delay(0.3f),
            Actions.run { textures.forEach { it.dispose() }; textures.clear() }
        ))
    }
}
