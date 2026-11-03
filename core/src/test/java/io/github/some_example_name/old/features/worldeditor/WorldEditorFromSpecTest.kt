package io.github.some_example_name.old.features.worldeditor

import kotlin.test.Test
import kotlin.test.assertEquals

/** Загрузка уровня в редактор: из рецепта должна получиться та же карта. */
class WorldEditorFromSpecTest {

    @Test
    fun `карта из рецепта совпадает с нарисованной`() {
        try {
            val original = WorldEditorViewModel("12345678")
            original.handle(WorldEditorIntent.SetDayNight(20))
            original.handle(WorldEditorIntent.SetSmoothing(7))
            original.handle(WorldEditorIntent.SetWorldWidth(64))
            original.handle(WorldEditorIntent.Paint(gridX = 10, gridY = 20))
            original.handle(WorldEditorIntent.ClearMap)

            val spec = original.toSpec()
            val restored = WorldEditorViewModel.fromSpec(spec)

            // toSpec() сверяет и размер, и сид, и параметры генератора, и правки.
            assertEquals(spec, restored.toSpec())
        }
        finally {
            // Companion генератора — общий на процесс: не оставляем его в состоянии теста.
            WorldGenerator.GENERATOR_DAY_NIGHT = 15
            WorldGenerator.GENERATOR_INTERPOLATE = 12
        }
    }
}
