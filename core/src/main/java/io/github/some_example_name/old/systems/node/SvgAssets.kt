package io.github.some_example_name.old.systems.node

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

/**
 * Кэш SVG-текстур нодов. Декодирование самого SVG (svg-salamander, java.awt)
 * делает desktop-бэкенд через loadSvg, чтобы core оставался cross-platform.
 */
object SvgAssets {
    private val cache = mutableMapOf<String, Texture>()

    var loadSvg: ((key: String, content: String?, width: Int, height: Int) -> Texture?)? = null

    /** Рендер 9-slice Condition.svg: + фиксированные полосы SVG, полости if/else растягиваются. */
    var loadCondition: ((path: String, width: Int, ifH: Int, elseH: Int) -> Texture?)? = null

    fun get(path: String, width: Int, height: Int): Texture {
        val key = "$path@${width}x$height"
        if (key !in cache) {
            cache[key] = loadSvg?.invoke(path, null, width, height) ?: Texture(Pixmap(1, 1, Pixmap.Format.RGBA8888))
        }
        return cache[key]!!
    }

    fun render(key: String, content: String, width: Int, height: Int): Texture {
        if (key !in cache) {
            cache[key] = loadSvg?.invoke(key, content, width, height) ?: Texture(Pixmap(1, 1, Pixmap.Format.RGBA8888))
        }
        return cache[key]!!
    }

    /** Рендер ConditionNode: 9-slice из оригинального SVG, полости растянуты под ifH/elseH.
     *  key — уникальный ключ конкретного экземпляра нода (не общий по размеру), чтобы
     *  refresh одного нода не разрушал текстуру, на которую ссылаются другие (палитра-превью). */
    fun renderCondition(key: String, path: String, width: Int, ifH: Int, elseH: Int): Texture {
        val ck = "cond@$key:$path:${width}x${ifH}x${elseH}"
        if (ck !in cache) {
            cache[ck] = loadCondition?.invoke(path, width, ifH, elseH) ?: Texture(Pixmap(1, 1, Pixmap.Format.RGBA8888))
        }
        return cache[ck]!!
    }

    fun drawable(path: String, width: Int, height: Int) = TextureRegionDrawable(TextureRegion(get(path, width, height)))

    fun drawable(key: String, content: String, width: Int, height: Int) = TextureRegionDrawable(TextureRegion(render(key, content, width, height)))

    fun conditionDrawable(key: String, path: String, width: Int, ifH: Int, elseH: Int) =
        TextureRegionDrawable(TextureRegion(renderCondition(key, path, width, ifH, elseH)))

    fun invalidate(key: String) {
        cache.remove(key)?.dispose()
    }

    /** Удаляет текстуру конкретного экземпляра нода (по его уникальному ключу). */
    fun invalidateCondition(key: String) {
        cache.keys.filter { it.startsWith("cond@$key") }.forEach {
            cache.remove(it)?.dispose()
        }
    }

    fun dispose() {
        cache.values.forEach { it.dispose() }
        cache.clear()
    }
}