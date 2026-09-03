package io.github.some_example_name.lwjgl3

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.kitfox.svg.SVGUniverse
import io.github.some_example_name.old.systems.node.SvgAssets
import java.awt.RenderingHints
import java.awt.image.BufferedImage

object SvgRenderer {
    fun install() {
        SvgAssets.loadSvg = { key, content, w, h -> render(key, content, w, h) }
        SvgAssets.loadCondition = { path, w, ifH, elseH -> renderCondition(path, w, ifH, elseH) }
    }

    /**
     * 9-slice рендер оригинального Condition.svg: рендерим SVG один раз в опорном
     * разрешении, режем на вертикальные полосы и растягиваем только полости if/else
     * по высоте под содержимое. Шапка/губа/низ сохраняют пропорции по ширине.
     */
    fun renderCondition(path: String, w: Int, ifH: Int, elseH: Int): Texture? {
        return try {
            val universe = SVGUniverse()
            val uri = Gdx.files.internal(path).read().use { stream ->
                universe.loadSVG(stream, path)
            }
            val diagram = universe.getDiagram(uri) ?: return null
            diagram.setIgnoringClipHeuristic(true)

            //Опорный рендер SVG. viewBox "16 16 168 168" -> 0..168. Используем x2 для резкости.
            val REF = 168 * 2
            diagram.setDeviceViewport(java.awt.Rectangle(0, 0, REF, REF))
            val src = BufferedImage(REF, REF, BufferedImage.TYPE_INT_ARGB)
            val sg = src.createGraphics()
            sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            diagram.render(sg)
            sg.dispose()

            //Границы полос в опорных координатах (REF) — пропорционально (uy-16)*2
            val hBot = 66 * 2
            val ifBot = 81 * 2
            val lipBot = 129 * 2
            val esBot = 145 * 2

            val scale = w / 168f
            val headerH = Math.round(66f * scale)
            val lipH = Math.round(48f * scale)
            val bottomH = Math.round(23f * scale)
            val outH = headerH + ifH + lipH + elseH + bottomH

            val out = BufferedImage(w, outH, BufferedImage.TYPE_INT_ARGB)
            val og = out.createGraphics()
            og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            var y = 0
            og.drawImage(src.getSubimage(0, 0, REF, hBot), 0, y, w, headerH, null); y += headerH
            og.drawImage(src.getSubimage(0, hBot, REF, ifBot - hBot), 0, y, w, ifH, null); y += ifH
            og.drawImage(src.getSubimage(0, ifBot, REF, lipBot - ifBot), 0, y, w, lipH, null); y += lipH
            og.drawImage(src.getSubimage(0, lipBot, REF, esBot - lipBot), 0, y, w, elseH, null); y += elseH
            og.drawImage(src.getSubimage(0, esBot, REF, REF - esBot), 0, y, w, bottomH, null); y += bottomH
            og.dispose()

            val pixmap = Pixmap(w, outH, Pixmap.Format.RGBA8888)
            val pixels = IntArray(w * outH)
            out.getRGB(0, 0, w, outH, pixels, 0, w)
            for (i in pixels.indices) {
                val px = pixels[i]
                val a = (px ushr 24) and 0xFF
                val r = (px ushr 16) and 0xFF
                val g2 = (px ushr 8) and 0xFF
                val b = px and 0xFF
                //premultiplied alpha под SpriteBatch
                pixmap.setColor(r * a / 255f / 255f, g2 * a / 255f / 255f, b * a / 255f / 255f, a / 255f)
                pixmap.fillRectangle(i % w, i / w, 1, 1)
            }
            Texture(pixmap).also {
                it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                pixmap.dispose()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun render(key: String, content: String?, w: Int, h: Int): Texture? {
        return try {
            val universe = SVGUniverse()
            val uri = if (content != null) {
                content.byteInputStream().use { universe.loadSVG(it, key) }
            } else {
                Gdx.files.internal(key).read().use { stream -> universe.loadSVG(stream, key) }
            }
            val diagram = universe.getDiagram(uri) ?: return null
            diagram.setIgnoringClipHeuristic(true)

            //svg-salamander парсит width="100%" как 100x100; руками задаём целевой размер
            diagram.setDeviceViewport(java.awt.Rectangle(0, 0, w, h))

            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            diagram.render(g)
            g.dispose()

            val pixmap = Pixmap(w, h, Pixmap.Format.RGBA8888)
            val pixels = IntArray(w * h)
            image.getRGB(0, 0, w, h, pixels, 0, w)
            for (i in pixels.indices) {
                val px = pixels[i]
                val a = (px ushr 24) and 0xFF
                val r = (px ushr 16) and 0xFF
                val g2 = (px ushr 8) and 0xFF
                val b = px and 0xFF
                //SpriteBatch смешивает с premultiplied alpha: RGB умножаем на a/255,
                //иначе полупрозрачные края (антиалиасинг) дают цветную "косу"/наклон на стенках
                pixmap.setColor(r * a / 255f / 255f, g2 * a / 255f / 255f, b * a / 255f / 255f, a / 255f)
                pixmap.fillRectangle(i % w, i / w, 1, 1)
            }

            Texture(pixmap).also {
                it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                pixmap.dispose()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}