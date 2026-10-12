package io.github.some_example_name.old.features.devsupport

import io.github.some_example_name.old.commands.GameCommand
import com.badlogic.gdx.Gdx
import io.github.some_example_name.old.journal.CommandSource
import io.github.some_example_name.old.journal.SessionJournal
import kotlinx.serialization.Serializable

data class CryptoWallet(
    val title: String,
    val address: String,
    val qrFile: String
)

/** Что игрок хочет сделать на экране поддержки. */
@Serializable
sealed interface SupportIntent : GameCommand {

    /**
     * @param title именно название кошелька, а не адрес: в журнал не кладут содержимое
     *   буфера обмена, даже когда оно публичное — это плохая привычка на будущее.
     */
    @Serializable
    data class CopyAddress(val title: String) : SupportIntent {
        override val name get() = "CopyAddress"
        override val detail get() = title
    }
}

class SupportViewModel {

    val wallets: List<CryptoWallet> = listOf(
        CryptoWallet(
            title = "USDT (TRC20 / TRON)",
            address = "TXVmZKM8K5NFcfJpYMgpWm9MpaLPADoC7f",
            qrFile = "ui/trc-20-qr.png"
        ),
        CryptoWallet(
            title = "TON",
            address = "UQANA9T_wuxvg73xQz-N7e-WfzDAf5uwMT0f6HIBQGCwEjBO",
            qrFile = "ui/ton-qr.png"
        )
    )

    fun handle(intent: SupportIntent) = SessionJournal.execute(CommandSource.SUPPORT, intent) { apply(it) }

    private fun apply(intent: SupportIntent) {
        when (intent) {
            is SupportIntent.CopyAddress -> {
                val wallet = wallets.firstOrNull { it.title == intent.title } ?: return
                Gdx.app.clipboard.contents = wallet.address
            }
        }
    }
}
