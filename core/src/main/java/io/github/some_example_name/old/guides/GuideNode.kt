package io.github.some_example_name.old.guides

/**
 * Базовый класс для ноды логики в системе гайдов.
 * Каждая нода представляет собой отдельное действие или условие,
 * которое может быть выполнено для подсказки игроку.
 */
abstract class GuideNode {
    /** Уникальный идентификатор ноды */
    abstract val id: String
    
    /** Отображаемое название ноды */
    abstract val title: String
    
    /** Описание того, что делает эта нода */
    abstract val description: String
    
    /** Входящие связи (от каких нод зависит эта) */
    val inputs: MutableList<GuideNodeConnection> = mutableListOf()
    
    /** Исходящие связи (какие ноды зависят от этой) */
    val outputs: MutableList<GuideNodeConnection> = mutableListOf()
    
    /** Позиция ноды в редакторе (для визуального представления) */
    var editorX: Float = 0f
    var editorY: Float = 0f
    
    /** Выполняется ли нода в данный момент */
    var isActive: Boolean = false
    
    /** Результат выполнения ноды */
    var result: GuideNodeResult? = null
    
    /**
     * Инициализация ноды перед выполнением
     */
    open fun initialize() {}
    
    /**
     * Выполнение логики ноды
     * @param context контекст выполнения с данными о текущем состоянии игры
     * @return результат выполнения
     */
    abstract fun execute(context: GuideExecutionContext): GuideNodeResult
    
    /**
     * Сброс состояния ноды
     */
    open fun reset() {
        isActive = false
        result = null
    }
    
    /**
     * Добавление входящей связи
     */
    fun addInput(connection: GuideNodeConnection) {
        inputs.add(connection)
    }
    
    /**
     * Добавление исходящей связи
     */
    fun addOutput(connection: GuideNodeConnection) {
        outputs.add(connection)
    }
}

/**
 * Типы нод для системы гайдов
 */
enum class GuideNodeType {
    /** Ноа-условие - проверяет некоторое состояние игры */
    CONDITION,
    
    /** Ноа-действие - выполняет действие или показывает подсказку */
    ACTION,
    
    /** Ноа-триггер - активируется при определенном событии */
    TRIGGER,
    
    /** Ноа-задержка - ожидает указанное время */
    DELAY,
    
    /** Ноа-ветвление - создает ветви в логике гайда */
    BRANCH,
    
    /** Ноа-завершение - заканчивает выполнение гайда */
    END
}

/**
 * Результат выполнения ноды
 */
sealed class GuideNodeResult {
    /** Нода успешно выполнена */
    object Success : GuideNodeResult()
    
    /** Нода еще выполняется (например, ожидание) */
    object Running : GuideNodeResult()
    
    /** Нода не может быть выполнена в текущих условиях */
    object Failed : GuideNodeResult()
    
    /** Нода была пропущена */
    object Skipped : GuideNodeResult()
}

/**
 * Связь между двумя нодами
 */
class GuideNodeConnection(
    val fromNode: GuideNode,
    val toNode: GuideNode,
    val outputIndex: Int = 0,
    val inputIndex: Int = 0
)

/**
 * Контекст выполнения для гайдов
 * Содержит всю необходимую информацию о текущем состоянии игры
 */
class GuideExecutionContext(
    /** Текущий уровень/миссия */
    val levelId: String,
    
    /** Время с начала уровня в тиках */
    val tickCount: Long,
    
    /** Позиция камеры */
    val cameraX: Float,
    val cameraY: Float,
    val cameraZoom: Float,
    
    /** Данные о клетках организма игрока */
    val playerCells: List<CellData>,
    
    /** Данные о клетках на карте */
    val worldCells: List<WorldCellData>,
    
    /** Текущие ресурсы */
    val resources: Map<String, Float>,
    
    /** Флаги состояний */
    val flags: MutableMap<String, Any> = mutableMapOf()
)

/**
 * Данные о клетке организма
 */
data class CellData(
    val id: Int,
    val type: String,
    val x: Float,
    val y: Float,
    val angle: Float,
    val properties: Map<String, Any>
)

/**
 * Данные о клетке мира
 */
data class WorldCellData(
    val gridX: Int,
    val gridY: Int,
    val isWall: Boolean,
    val resourceType: String?,
    val resourceAmount: Float
)
