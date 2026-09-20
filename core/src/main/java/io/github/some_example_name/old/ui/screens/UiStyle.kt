package io.github.some_example_name.old.ui.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.kotcrab.vis.ui.widget.VisSlider
import com.kotcrab.vis.ui.widget.VisTextField
import com.kotcrab.vis.ui.widget.VisTextButton
import com.kotcrab.vis.ui.widget.VisWindow

val STYLE_BEIGE = Color(0.84f, 0.77f, 0.62f, 1.00f)
private val BTN_UP  = Color(0.16f, 0.16f, 0.18f, 0.82f)
private val BTN_OVR = Color(0.28f, 0.27f, 0.30f, 0.90f)
private val BTN_DWN = Color(0.38f, 0.37f, 0.42f, 0.95f)
private val BTN_CHK = Color(0.40f, 0.36f, 0.26f, 0.95f)

fun fillRoundedRect(p: Pixmap, x: Int, y: Int, w: Int, h: Int, r: Int) {
    if (r <= 0) { p.fillRectangle(x, y, w, h); return }
    p.fillRectangle(x + r, y,     w - 2 * r, h)
    p.fillRectangle(x,     y + r, w,         h - 2 * r)
    p.fillCircle(x + r,         y + r,         r)
    p.fillCircle(x + w - r - 1, y + r,         r)
    p.fillCircle(x + r,         y + h - r - 1, r)
    p.fillCircle(x + w - r - 1, y + h - r - 1, r)
}

private fun linearTex(p: Pixmap, textures: MutableList<Texture>): Texture {
    val t = Texture(p)
    t.setFilter(TextureFilter.Linear, TextureFilter.Linear)
    p.dispose()
    textures += t
    return t
}

// 64×64 NinePatch with linear filtering — smooth rounded corners at any display size
fun makeStyledNP(fill: Color, border: Color, textures: MutableList<Texture>): NinePatchDrawable {
    val sz = 64; val r = 14; val bw = 2
    val p = Pixmap(sz, sz, Pixmap.Format.RGBA8888)
    p.blending = Pixmap.Blending.None
    p.setColor(0f, 0f, 0f, 0f); p.fill()
    p.setColor(border); fillRoundedRect(p, 0,  0,  sz,          sz,          r)
    p.setColor(fill);   fillRoundedRect(p, bw, bw, sz - 2 * bw, sz - 2 * bw, r - bw)
    return NinePatchDrawable(NinePatch(linearTex(p, textures), r, r, r, r))
}

/**
 * @param toggle  true = distinct checked/on visual (pause, draw rays, etc.)
 */
fun makeStyledButton(text: String, game: MyGame, textures: MutableList<Texture>, toggle: Boolean = false): VisTextButton {
    val style = VisTextButton.VisTextButtonStyle()   // fresh — no VisUI skin artifacts
    style.font          = game.buttonFont
    style.fontColor     = Color(STYLE_BEIGE)
    style.overFontColor = Color.WHITE
    style.downFontColor = Color.WHITE
    style.up   = makeStyledNP(BTN_UP,  Color(STYLE_BEIGE).also { it.a = 0.75f }, textures)
    style.over = makeStyledNP(BTN_OVR, Color(STYLE_BEIGE), textures)
    style.down = makeStyledNP(BTN_DWN, Color.WHITE, textures)
    if (toggle) {
        style.checked          = makeStyledNP(BTN_CHK, Color(STYLE_BEIGE), textures)
        style.checkedFontColor = Color.WHITE
    }
    val d = Gdx.graphics.density
    return VisTextButton(text, style).also { it.pad(10f * d, 24f * d, 10f * d, 24f * d) }
}

// ── Dialog window rounded corners ───────────────────────────────────────────

private var _dialogBgDrawable: NinePatchDrawable? = null

private fun getDialogBackground(): NinePatchDrawable {
    if (_dialogBgDrawable == null) {
        val sz = 64; val r = 16; val bw = 2
        val p = Pixmap(sz, sz, Pixmap.Format.RGBA8888)
        p.blending = Pixmap.Blending.None
        p.setColor(0f, 0f, 0f, 0f); p.fill()
        p.setColor(Color(STYLE_BEIGE).also { it.a = 0.40f })
        fillRoundedRect(p, 0, 0, sz, sz, r)
        p.setColor(Color(0.10f, 0.10f, 0.12f, 0.97f))
        fillRoundedRect(p, bw, bw, sz - 2 * bw, sz - 2 * bw, r - bw)
        val tex = Texture(p); tex.setFilter(TextureFilter.Linear, TextureFilter.Linear); p.dispose()
        _dialogBgDrawable = NinePatchDrawable(NinePatch(tex, r, r, r, r))
    }
    return _dialogBgDrawable!!
}

fun VisWindow.roundCorners() {
    val d = Gdx.graphics.density
    val side = 16f * d

    // Only replace the background drawable — do NOT call setStyle(), which would
    // overwrite subclass-specific style objects (e.g. ColorPickerStyle).
    setBackground(getDialogBackground())

    // Pad title bar (title text + X button) away from rounded corners
    getTitleTable().apply {
        padLeft(side);  padRight(side)
        padTop(8f * d); padBottom(8f * d)
    }
    // Expand padTop so the window allocates enough room for the padded title table
    padTop(padTop + 16f * d)

    // Pad the content area on remaining three sides
    padLeft(side); padRight(side); padBottom(side)
}

// ── Slider ──────────────────────────────────────────────────────────────────

fun makeStyledSlider(
    min: Float, max: Float, step: Float, vertical: Boolean,
    textures: MutableList<Texture>
): VisSlider {
    val d = Gdx.graphics.density

    // Track: 64×8, pill-shaped, linear filtered
    val tW = 64; val tH = 8
    val tp = Pixmap(tW, tH, Pixmap.Format.RGBA8888)
    tp.blending = Pixmap.Blending.None
    tp.setColor(0f, 0f, 0f, 0f); tp.fill()
    tp.setColor(Color(0.30f, 0.27f, 0.20f, 0.55f))
    fillRoundedRect(tp, 0, 0, tW, tH, tH / 2)
    val trackTex = linearTex(tp, textures)

    // Filled portion before knob
    val fp = Pixmap(tW, tH, Pixmap.Format.RGBA8888)
    fp.blending = Pixmap.Blending.None
    fp.setColor(0f, 0f, 0f, 0f); fp.fill()
    fp.setColor(Color(STYLE_BEIGE).also { it.a = 0.65f })
    fillRoundedRect(fp, 0, 0, tW, tH, tH / 2)
    val fillTex = linearTex(fp, textures)

    // Knob: 64×64 high-res circle, displayed at 20dp — linear filter = smooth
    val kSz = 64
    val kp = Pixmap(kSz, kSz, Pixmap.Format.RGBA8888)
    kp.blending = Pixmap.Blending.None
    kp.setColor(0f, 0f, 0f, 0f); kp.fill()
    kp.setColor(Color(STYLE_BEIGE));            kp.fillCircle(kSz / 2, kSz / 2, kSz / 2 - 1)
    kp.setColor(Color(0.22f, 0.20f, 0.15f, 1f)); kp.fillCircle(kSz / 2, kSz / 2, kSz / 2 - 8)
    val knobTex = linearTex(kp, textures)

    // Display sizes (density-scaled, independent of texture resolution)
    val trackH  = 6f  * d
    val knobSz  = 20f * d

    val trackD = NinePatchDrawable(NinePatch(trackTex, tH / 2, tH / 2, 0, 0)).also { it.minHeight = trackH }
    val fillD  = NinePatchDrawable(NinePatch(fillTex,  tH / 2, tH / 2, 0, 0)).also { it.minHeight = trackH }
    val knobD  = TextureRegionDrawable(TextureRegion(knobTex)).also {
        it.minWidth  = knobSz
        it.minHeight = knobSz
    }

    val style = Slider.SliderStyle()
    style.background = trackD
    style.knobBefore = fillD
    style.knob       = knobD
    style.knobOver   = knobD
    style.knobDown   = knobD

    return VisSlider(min, max, step, vertical, style)
}

fun makeStyledSlider(min: Float, max: Float, step: Float, vertical: Boolean): VisSlider =
    makeStyledSlider(min, max, step, vertical, mutableListOf())

// ── TextField ────────────────────────────────────────────────────────────────

fun makeStyledTextField(game: MyGame, textures: MutableList<Texture>): VisTextField {
    val sz = 64; val r = 12; val bw = 2

    val np = Pixmap(sz, sz, Pixmap.Format.RGBA8888)
    np.blending = Pixmap.Blending.None
    np.setColor(0f, 0f, 0f, 0f); np.fill()
    np.setColor(Color(STYLE_BEIGE).also { it.a = 0.45f }); fillRoundedRect(np, 0, 0, sz, sz, r)
    np.setColor(Color(0.12f, 0.12f, 0.14f, 0.90f)); fillRoundedRect(np, bw, bw, sz - 2 * bw, sz - 2 * bw, r - bw)
    val bgTex = linearTex(np, textures)

    val fp = Pixmap(sz, sz, Pixmap.Format.RGBA8888)
    fp.blending = Pixmap.Blending.None
    fp.setColor(0f, 0f, 0f, 0f); fp.fill()
    fp.setColor(Color(STYLE_BEIGE)); fillRoundedRect(fp, 0, 0, sz, sz, r)
    fp.setColor(Color(0.15f, 0.14f, 0.17f, 0.95f)); fillRoundedRect(fp, bw, bw, sz - 2 * bw, sz - 2 * bw, r - bw)
    val focusTex = linearTex(fp, textures)

    val cp = Pixmap(2, 32, Pixmap.Format.RGBA8888)
    cp.setColor(Color(STYLE_BEIGE)); cp.fill()
    val cursorTex = linearTex(cp, textures)

    val sp = Pixmap(1, 1, Pixmap.Format.RGBA8888)
    sp.setColor(Color(STYLE_BEIGE).also { it.a = 0.28f }); sp.fill()
    val selTex = linearTex(sp, textures)

    val style = VisTextField.VisTextFieldStyle()
    style.background        = NinePatchDrawable(NinePatch(bgTex,    r, r, r, r))
    style.focusedBackground = NinePatchDrawable(NinePatch(focusTex, r, r, r, r))
    style.font              = game.largeFont
    style.fontColor         = Color(STYLE_BEIGE)
    style.focusedFontColor  = Color.WHITE
    style.cursor            = TextureRegionDrawable(TextureRegion(cursorTex))
    style.selection         = TextureRegionDrawable(TextureRegion(selTex))

    return VisTextField("", style)
}
