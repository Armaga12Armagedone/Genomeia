package io.github.some_example_name.old.core.ui

import io.github.some_example_name.old.core.DIGameGlobalContainer
import io.github.some_example_name.render.RenderMode

/**
 * Выпадающий список режима рендера — один на все экраны, где режим можно выбрать.
 *
 * Пункты — в порядке [RenderMode], подписи — из бандла по имени режима
 * (renderMode.realistic, renderMode.debug, renderMode.nostalgic). Где хранить выбор,
 * решает экран: у симуляции и редактора генома режимы свои и друг на друга не влияют.
 */
fun renderModeSelectBox(
    selected: RenderMode,
    onSelect: (RenderMode) -> Unit
): StyledSelectBox<String> {
    val modes = RenderMode.entries
    return styledSelectBox(
        items = modes
            .map { DIGameGlobalContainer.bundle.get("renderMode." + it.name.lowercase()) }
            .toTypedArray(),
        selectedIndex = modes.indexOf(selected),
        onChange = { _, index -> onSelect(modes[index]) }
    )
}
