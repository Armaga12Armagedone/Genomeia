package io.github.some_example_name.old.ui.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Timer
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextButton
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.ui.core.dp
import io.github.some_example_name.old.ui.core.h
import io.github.some_example_name.old.ui.core.repeat
import io.github.some_example_name.old.ui.core.visImage
import io.github.some_example_name.old.ui.core.visLabel
import io.github.some_example_name.old.ui.core.visTable
import io.github.some_example_name.old.ui.core.visTextButton
import io.github.some_example_name.old.ui.core.w

class VisSupportScreen : VisScreen(isScrollable = true) {

    private val qrTextures = mutableListOf<Texture>()

    override fun VisTable.compose() {
        visTextButton(
            text = bundle.get("label.backToMenu"),
            onClick = { game.screen = MenuScreen() }
        ) {
            left()
            padTop(32.dp())
        }
        row()

        visLabel(bundle.get("label.support")) {
            center()
            padBottom(25f.dp())
        }
        row()

        visLabel(bundle.get("label.supportText")) {
            center()
            padBottom(35f.dp())
            width(w * 0.9f)
        }.apply {
            setWrap(true)
        }
        row()

        data class Crypto(
            val title: String,
            val address: String,
            val qrFile: String
        )

        val cryptos = listOf(
            Crypto (
                title = "USDT (TRC20 / TRON)",
                address = "TXVmZKM8K5NFcfJpYMgpWm9MpaLPADoC7f",
                qrFile = "ui/trc-20-qr.png"
            ),
            Crypto(
                title = "TON",
                address = "UQANA9T_wuxvg73xQz-N7e-WfzDAf5uwMT0f6HIBQGCwEjBO",
                qrFile = "ui/ton-qr.png"
            )
        )

        repeat(2) { index ->
            visTable(backgroundColor = Color(0.12f, 0.15f, 0.18f, 1f)) {
                visLabel(cryptos[index].title) {
                    colspan(2)
                    padBottom(15f.dp())
                }
                row()

                visLabel(cryptos[index].address) {
                    width(w * 0.4f)
                    padBottom(12f.dp())
                }.apply {
                    setWrap(true)
                }
                row()

                var copyBtn: VisTextButton? = null
                copyBtn = visTextButton(bundle.get("label.copy"), onClick = {
                    Gdx.app.clipboard.contents = cryptos[index].address
                    copyBtn?.setText(bundle.get("label.copied"))
                    Timer.schedule(object : Timer.Task() {
                        override fun run() {
                            copyBtn?.setText(bundle.get("label.copy"))
                        }
                    }, 1.5f)
                })
                row()

                try {
                    val qrTexture = Texture(Gdx.files.internal(cryptos[index].qrFile)).also { qrTextures += it }
                    visImage(qrTexture) {
                        size( h * 0.3f, h * 0.3f)
                        colspan(2)
                        padTop(18f.dp())
                        padBottom(64.dp())
                    }
                } catch (e: Exception) {
                    throw Exception("No qr for crypto")
                }
            }
            row()
        }
        row()
    }

    override fun dispose() {
        qrTextures.forEach { it.dispose() }
        qrTextures.clear()
        super.dispose()
    }
}
