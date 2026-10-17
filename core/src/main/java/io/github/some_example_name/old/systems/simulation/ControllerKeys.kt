package io.github.some_example_name.old.systems.simulation

/**
 * Клавиши контроллера: номер клавиши в SimulationData.controllerKeys и её имя.
 *
 * Номер — бит в маске, которую журнал .bug пишет событием ControllerKeys, и индекс кнопки
 * экранной клавиатуры (SimulationScreen). Имя — то, чем клавиша записана в геноме у
 * клетки Controller (ControllerData.key): оно постоянное, номер в геном не попадает.
 */
object ControllerKeys {

    val NAMES = arrayOf(
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
        "W", "A", "S", "D",
        "SPACE",
        "UP", "LEFT", "DOWN", "RIGHT"
    )

    val COUNT = NAMES.size

    /** Номер клавиши по имени, -1 — такой клавиши нет. */
    fun indexOf(name: String): Int = NAMES.indexOf(name)
}
