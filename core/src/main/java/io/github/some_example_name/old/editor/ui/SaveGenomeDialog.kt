package io.github.some_example_name.old.editor.ui

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.I18NBundle
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisLabel
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.core.DISimulationContainer.simulationSystem
import io.github.some_example_name.old.systems.genomics.genome.Genome
import io.github.some_example_name.old.systems.genomics.genome.GenomeJsonReader
import io.github.some_example_name.old.ui.dialogs.setupTitleSize
import io.github.some_example_name.old.ui.screens.MyGame
import io.github.some_example_name.old.ui.screens.applyCustomFont
import io.github.some_example_name.old.ui.screens.makeStyledButton
import io.github.some_example_name.old.ui.screens.makeStyledTextField

class SaveGenomeDialog(
    val genomeJsonReader: GenomeJsonReader,
    val genome: Genome,
    val onSaveAndTest: (String) -> Unit,
    val onGoMenu: () -> Unit,
    val game: MyGame,
    val bundle: I18NBundle,
    isGoToMenu: Boolean
) : VisDialog(bundle.get("button.saveGenome")) {

    private val textures = mutableListOf<Texture>()

    init {
        setupTitleSize(game)

        val density = Gdx.graphics.density
        val btnH    = Gdx.graphics.height * 0.065f
        val pad     = 8f * density

        // ── Top row: Name label + input field ──────────────────────────────
        val nameRow = Table()
        val genomeText = VisLabel(bundle.get("button.name"))
        game.applyCustomFont(genomeText)
        val genomeNameField = makeStyledTextField(game, textures).also { it.text = genome.name }

        nameRow.add(genomeText).padRight(pad)
        nameRow.add(genomeNameField).growX().height(btnH * 0.9f)
        contentTable.add(nameRow).growX().padBottom(pad).row()

        // ── Bottom row: action buttons side by side ─────────────────────────
        val btnRow = Table()
        btnRow.defaults().height(btnH).padRight(pad)

        val saveToFileAndTestButton = makeStyledButton(bundle.get("button.saveAndTest"), game, textures)
        val saveToFileButton        = makeStyledButton(bundle.get("button.saveToFile"),  game, textures)

        saveToFileAndTestButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor?) {
                genomeJsonReader.saveGenomeToFile(genome, "user_genomes/${genomeNameField.text}.json", name = genomeNameField.text)
                onSaveAndTest.invoke("${genomeNameField.text}.json")
                val genomeNames = DISimulationContainer.genomeManager.genomes.map { it.name }
                simulationSystem.simulationData.currentGenomeIndex = genomeNames.indexOf(genomeNameField.text)
                fadeOut()
            }
        })
        saveToFileButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor?) {
                genomeJsonReader.saveGenomeToFile(genome, "user_genomes/${genomeNameField.text}.json", genomeNameField.text)
            }
        })

        btnRow.add(saveToFileAndTestButton)
        btnRow.add(saveToFileButton)

        val exportButton = if (Gdx.app.type == Application.ApplicationType.Android) {
            makeStyledButton(bundle.get("button.saveAndExport"), game, textures).also {
                it.addListener(object : ChangeListener() {
                    override fun changed(event: ChangeEvent, actor: Actor?) {
                        genomeJsonReader.saveGenomeToFile(genome, "user_genomes/${genomeNameField.text}.json", genomeNameField.text)
                        game.multiPlatformFileProvider.exportGenome("user_genomes/${genomeNameField.text}.json")
                    }
                })
                btnRow.add(it).height(btnH).padRight(pad)
            }
        } else null

        if (isGoToMenu) {
            makeStyledButton(bundle.get("button.menu"), game, textures).also {
                it.addListener(object : ChangeListener() {
                    override fun changed(event: ChangeEvent, actor: Actor?) { onGoMenu.invoke() }
                })
                btnRow.add(it).height(btnH).padRight(0f)
            }
        }

        contentTable.add(btnRow).center().padBottom(pad)

        // ── Validation ──────────────────────────────────────────────────────
        val isDisabled = genomeNameField.text.isEmpty()
        saveToFileButton.isDisabled            = isDisabled
        saveToFileAndTestButton.isDisabled     = isDisabled
        exportButton?.isDisabled               = isDisabled

        genomeNameField.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                val d = genomeNameField.text.isEmpty()
                saveToFileButton.isDisabled        = d
                saveToFileAndTestButton.isDisabled = d
                exportButton?.isDisabled           = d
            }
        })

        contentTable.pad(pad)
        pack()
        centerWindow()
    }

    override fun hide(action: Action?) {
        super.hide(action)
        addAction(Actions.sequence(
            Actions.delay(0.3f),
            Actions.run { textures.forEach { it.dispose() }; textures.clear() }
        ))
    }
}
