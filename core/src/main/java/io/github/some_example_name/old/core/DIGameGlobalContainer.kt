package io.github.some_example_name.old.core

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.I18NBundle
import com.badlogic.gdx.utils.Json
import io.github.some_example_name.old.cells.base.CellListBuilder
import io.github.some_example_name.old.systems.genomics.Morphogenesis
import io.github.some_example_name.old.systems.genomics.genome_deprecated.GenomeJsonReader
import io.github.some_example_name.old.systems.render.ShaderManager
import io.github.some_example_name.old.systems.render.ShaderManagerLibgdxApi
import io.github.some_example_name.old.game.MyGame
import java.util.Locale

object DIGameGlobalContainer {

    lateinit var game: MyGame


    lateinit var fileProvider: FileProvider
    val json by lazy { Json() }
    val bundle: I18NBundle by lazy {
        I18NBundle.createBundle(
            Gdx.files.internal("ui/i18n/MyBundle"),
            Locale.getDefault()
        )
    }

    val genomeJsonReader = GenomeJsonReader()

    private val cellListBuilder = CellListBuilder()

    val particleTexturePaths: List<String> = cellListBuilder.instances.map {
        "cell_textures/" + it.textureName
    } + "cell_textures/not_cell.png"

    val defaultCellSettingsMap = cellListBuilder.instances.associate {
        it.name to it.defaultCellSettings
    }

    val shaderManager: ShaderManager = ShaderManagerLibgdxApi(particleTexturePaths)

    val morphogenesis = Morphogenesis()

    val substrateSettings = SubstrateSettings()
}
