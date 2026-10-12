package io.github.some_example_name.old.bug

import io.github.some_example_name.old.commands.GameCommand

/**
 * Экран, которому повтор бага (BugReplay) подаёт записанные команды. Навигацию повтор
 * выполняет сам, всё остальное отдаёт текущему экрану: только он знает свой обработчик.
 */
interface ReplayTarget {
    /**
     * Выполнить записанную команду так же, как её выполнило бы нажатие, но без окон,
     * которые нажатие открыло бы следом: при повторе их некому закрыть.
     *
     * @return false — команда не этого экрана: повтор разошёлся с записью.
     */
    fun replay(command: GameCommand): Boolean

    /** Повтор закончился: показать итог (например, отметить паузу — мир остановлен). */
    fun onReplayFinished() {}
}
