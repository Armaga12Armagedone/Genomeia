package io.github.some_example_name.old.systems.simulation

class SimulationData {
    var isRestart = false
    var isFinish = false
    var tickCounter = 0
    var timeSimulation = 0f

    //Пока вынес все глобальные переменные сюда, но кажется это все не совсем к месту
    var currentGenomeIndex = 0

    var isPlay = true
    var maxSpeed = false

    var targetUPS: Int = 60
        set(value) {
            field = value.coerceIn(1, 1000)
        }
    var ups = 60
    var selectedCellIndex = -1

    var showControllerKeys = false
    val controllerKeyTouched = BooleanArray(19)

    /**
     * Отладочный стресс-тест жизненного цикла клеток: пока клавиша зажата, каждый тик
     * несколько случайных клеток умирают (A) или получают предельную скорость и улетают,
     * рвя свои связи (D).
     *
     * Нужен, чтобы не воспроизводить руками сценарии, на которых всплывают гонки и битые
     * ссылки в связях: они возникают именно при активной смерти клеток и разрыве связей,
     * а руками такое повторять долго и ненадёжно.
     *
     * Пишется потоком рендера, читается потоком симуляции — как и controllerKeyTouched.
     * Обычные Boolean без volatile: тут не нужна ни атомарность, ни немедленная видимость,
     * задержка в несколько тиков ничего не меняет.
     *
     * Работает только при DEBUG_CHECKS: в обычной сборке флаги никем не выставляются.
     */
    var debugKillCells = false
    var debugFlingCells = false

    fun clear() {
        isRestart = false
        isFinish = false
        tickCounter = 0
        timeSimulation = 0f
        selectedCellIndex = -1
    }

}
