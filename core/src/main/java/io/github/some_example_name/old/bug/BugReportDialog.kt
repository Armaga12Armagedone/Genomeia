package io.github.some_example_name.old.bug

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Timer
import com.kotcrab.vis.ui.widget.VisDialog
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.VisTextArea
import com.kotcrab.vis.ui.widget.VisTextField
import io.github.some_example_name.old.core.DIGameGlobalContainer.bundle
import io.github.some_example_name.old.core.DIGameGlobalContainer.game
import io.github.some_example_name.old.core.ui.dp
import io.github.some_example_name.old.core.ui.globalVisTable
import io.github.some_example_name.old.core.ui.h
import io.github.some_example_name.old.core.ui.makeStyledTextField
import io.github.some_example_name.old.core.ui.setupTitleSize
import io.github.some_example_name.old.core.ui.visLabel
import io.github.some_example_name.old.core.ui.visTable
import io.github.some_example_name.old.core.ui.visTextButton
import io.github.some_example_name.old.core.ui.w

/**
 * Окно отчёта об ошибке: пояснение игрока и что сделать с .bug — сохранить, поделиться,
 * отправить на сервер (пока заглушка, см. BugUploader).
 *
 * Отчёт собирается не при открытии окна, а при первой кнопке: в него должно попасть
 * пояснение. Для краша .bug уже собран обработчиком, и пояснение дописывается в него.
 * Поменяли текст после сохранения — следующая кнопка перепишет файл.
 *
 * Само окно и всё, что в нём делается, — не команды игры: в журнал оно не пишется.
 */
class BugReportDialog(
    private val kind: BugKind,
    /** Уже собранный .bug (краш) или null — соберётся при первой кнопке. */
    existing: FileHandle?,
    private val screenshot: ByteArray?,
    /** Уже набранное пояснение — когда окно краша сменяет недописанный отчёт. */
    private val initialDescription: String = "",
    private val onClosed: () -> Unit
) : VisDialog(bundle.get(if (kind == BugKind.CRASH) "bug.title.crash" else "bug.title.report")) {

    private var bug: FileHandle? = existing
    private var writtenDescription = ""
    private val textures = mutableListOf<Texture>()
    private lateinit var description: VisTextArea
    private lateinit var status: VisLabel
    private var closed = false

    init {
        setupTitleSize(game)
        isModal = true
        isMovable = true

        val root = globalVisTable { compose() }
        contentTable.add(root).width(minOf(w * 0.9f, 640f.dp())).maxHeight(h * 0.9f)

        closeOnEscape()
        pack()
        centerWindow()
    }

    private fun VisTable.compose() {
        defaults().pad(6.dp())

        visLabel(
            text = bundle.get(if (kind == BugKind.CRASH) "bug.text.crash" else "bug.text.report"),
            font = game.mediumFont
        ) { growX() }.setWrap(true)
        row()

        val style = makeStyledTextField(game, textures).style as VisTextField.VisTextFieldStyle
        description = VisTextArea(initialDescription, style).apply {
            messageText = bundle.get("bug.hint")
            setPrefRows(4f)
        }
        add(description).growX().minHeight(110f.dp())
        row()

        status = visLabel("", font = game.mediumFont, align = Align.center) { growX() }
        status.setWrap(true)
        row()

        // Сеткой 2×2: в один ряд четыре кнопки не влезают в окно на телефоне.
        visTable({ growX() }) {
            defaults().pad(4.dp()).growX().uniformX()
            visTextButton(bundle.get("bug.save"), onClick = { save() })

            val isPhone = Gdx.app.type == Application.ApplicationType.Android
            visTextButton(bundle.get(if (isPhone) "bug.share" else "bug.openFolder"), onClick = { share() })
            row()

            visTextButton(bundle.get("bug.send"), onClick = { send() })
            visTextButton(bundle.get("bug.close"), onClick = { close() })
        }
    }

    /** Что игрок успел написать. */
    fun typedDescription(): String = description.text.trim()

    /** .bug с текущим пояснением: собрать, если ещё нет, или переписать пояснение. */
    private fun ensureBug(): FileHandle? {
        val text = description.text.trim()
        return try {
            val current = bug
            when {
                current == null -> BugReports.create(kind, text, screenshotPng = screenshot).also { bug = it }
                text != writtenDescription -> current.also { BugReports.rewriteDescription(it, text) }
                else -> current
            }.also { writtenDescription = text }
        } catch (e: Exception) {
            Gdx.app.error("BugReport", "не удалось собрать отчёт", e)
            status.setText(bundle.get("bug.failed") + (e.message ?: e::class.java.simpleName))
            null
        }
    }

    private fun save() {
        val file = ensureBug() ?: return
        status.setText(bundle.get("bug.saved") + file.file().absolutePath)
        pack()
    }

    private fun share() {
        val file = ensureBug() ?: return
        if (!game.multiPlatformFileProvider.shareFile(file.file(), bundle.get("bug.title.report"))) {
            status.setText(bundle.get("bug.saved") + file.file().absolutePath)
            pack()
        }
    }

    private fun send() {
        val file = ensureBug() ?: return
        status.setText(bundle.get("bug.sending"))
        BugUploader.upload(file) { success, message ->
            status.setText(if (success) bundle.get("bug.sent") else bundle.get("bug.sendFailed") + message)
            pack()
        }
    }

    public override fun close() {
        if (!closed) {
            closed = true
            // Пояснение, набранное после последней кнопки, не должно потеряться.
            if (bug != null && description.text.trim() != writtenDescription) ensureBug()
            if (kind == BugKind.CRASH) bug?.let { BugReports.clearPendingCrash(it) }
            onClosed()
        }
        super.close()
        // Текстуры поля ввода — после того, как окно доиграет исчезновение.
        Timer.schedule(object : Timer.Task() {
            override fun run() {
                textures.forEach { it.dispose() }
                textures.clear()
            }
        }, TEXTURE_DISPOSE_DELAY)
    }

    private companion object {
        const val TEXTURE_DISPOSE_DELAY = 0.6f
    }
}
