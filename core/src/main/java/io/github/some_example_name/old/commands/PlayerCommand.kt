package io.github.some_example_name.old.commands

/**
 * Касание мира в симуляции. Применяется потоком симуляции в фазе команд игрока, поэтому
 * и в запись сессии (LogSaver) попадает с номером тика, в котором применилась. В [detail] координаты округлены:
 * доли клетки в журнале не нужны.
 */
sealed interface PlayerCommand : GameCommand {

    data class TouchDown(val x: Float, val y: Float, val isLeftButton: Boolean) : PlayerCommand {
        override val name get() = "TouchDown"
        override val detail get() = "${x.toInt()},${y.toInt()} ${if (isLeftButton) "L" else "R"}"
    }

    data class Drag(val x: Float, val y: Float, val dx: Float, val dy: Float) : PlayerCommand {
        override val name get() = "Drag"
        override val detail get() = "${x.toInt()},${y.toInt()}"
    }

    class Tap(
        val x: Float,
        val y: Float,
        val isLeftButton: Boolean = true,
        val genomeIndex: Int? = null
    ) : PlayerCommand {
        override val name get() = "Tap"
        override val detail
            get() = "${x.toInt()},${y.toInt()} ${if (isLeftButton) "L" else "R"} genome=${genomeIndex ?: "current"}"
    }

    object StopDrag : PlayerCommand {
        override val name get() = "StopDrag"
    }
}
