package io.github.some_example_name.old.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.kotcrab.vis.ui.widget.VisTable

// ==================== MINI DIALOG / HOVER POPUP (улучшенная версия) ====================

private var _popupBackground: NinePatchDrawable? = null

private fun getPopupBackground(): NinePatchDrawable {
    if (_popupBackground == null) {
        _popupBackground = makeStyledNP(
            fill = Color(0.14f, 0.13f, 0.11f, 0.96f),
            border = Color(STYLE_BEIGE).also { it.a = 0.65f },
            textures = mutableListOf()
        )
    }
    return _popupBackground!!
}

/** Внутренняя функция позиционирования (над указателем или под ним + центрирование) */
private fun positionPopupSmart(
    popup: VisTable,
    pointerX: Float,
    pointerY: Float,
    stage: Stage
) {
    val pw = popup.width
    val ph = popup.height
    val gap = 10f.dp()
    val margin = 8f.dp()
    val stageW = stage.width
    val stageH = stage.height

    // Пробуем разместить НАД указателем (по центру)
    var x = pointerX - pw / 2f
    var y = pointerY + gap

    // Если не влезает сверху — размещаем ПОД указателем
    if (y + ph > stageH - margin) {
        y = pointerY - ph - gap
    }

    // Не вылезаем за края по X
    x = x.coerceIn(margin, stageW - pw - margin)

    popup.setPosition(x, y)
}

/**
 * Показать мини-диалог в любой точке экрана (для НЕ-UI элементов).
 * Ты полностью контролируешь появление и исчезновение.
 *
 * Пример использования с игровым объектом:
 * val screenPos = camera.project(worldPos)
 * val popup = showMiniDialog(stage, screenPos.x, screenPos.y) {
 *     visLabel("HP: 87")
 *     row()
 *     visLabel("Уровень: 12")
 * }
 * // позже
 * hideMiniDialog(popup)
 */
fun showMiniDialog(
    stage: Stage,
    stageX: Float,
    stageY: Float,
    init: VisTable.() -> Unit
): VisTable {
    val content = VisTable(true).apply {
        background = getPopupBackground()
        defaults().pad(12f.dp(), 16f.dp(), 12f.dp(), 16f.dp())
        init()
    }

    content.pack()                    // ← важно! убирает моргание и даёт правильный размер
    stage.addActor(content)
    positionPopupSmart(content, stageX, stageY, stage)

    return content
}

fun hideMiniDialog(dialog: VisTable) {
    dialog.remove()
}

/**
 * Автоматический hoverPopup для Actor (UI-элементы).
 * Используй так же, как раньше — теперь с красивым фоном и умным позиционированием.
 */
fun Actor.hoverPopup(
    init: VisTable.() -> Unit
): Actor {
    val target = this
    var tooltip: VisTable? = null
    var isTouchInitiated = false

    fun hidePopup() {
        tooltip?.remove()
        tooltip = null
        isTouchInitiated = false
    }

    fun showPopup(event: InputEvent?) {
        if (tooltip != null) return

        val content = VisTable(true).apply {
            background = getPopupBackground()
            defaults().pad(12f.dp(), 16f.dp(), 12f.dp(), 16f.dp())
            init()
        }

        tooltip = content
        val stg = target.stage ?: return
        stg.addActor(content)

        content.pack()

        val sx = event?.stageX ?: Gdx.input.x.toFloat()
        val sy = event?.stageY ?: Gdx.input.y.toFloat()
        positionPopupSmart(content, sx, sy, stg)

        // Слушатель на самом попапе (чтобы не закрывался при наведении на него)
        content.addListener(object : InputListener() {
            override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
                if (toActor != target) hidePopup()
            }
        })
    }

    val listener = object : InputListener() {
        override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
            if (tooltip == null && event != null) {
                showPopup(event)
                isTouchInitiated = pointer != -1
            }
        }

        override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
            if (toActor != tooltip) hidePopup()
        }

        override fun mouseMoved(event: InputEvent?, x: Float, y: Float): Boolean {
            tooltip?.let { t ->
                val stg = stage ?: return false
                positionPopupSmart(t, event!!.stageX, event.stageY, stg)
            }
            return false
        }

        override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            if (tooltip == null && event != null) {
                showPopup(event)
                isTouchInitiated = pointer != -1
            }
            return false
        }

        override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            tooltip?.let { t ->
                val stg = stage ?: return
                positionPopupSmart(t, event!!.stageX, event.stageY, stg)
            }
        }

        override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
            if (isTouchInitiated) hidePopup()
        }
    }

    target.addListener(listener)
    return target
}
