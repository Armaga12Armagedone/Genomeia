package io.github.some_example_name.old.guides

/**
 * Стартовая нода - точка входа в гайд
 */
class StartGuideNode(
    override val id: String,
    override val title: String = "Start",
    override val description: String = "Начало гайда"
) : GuideNode() {
    
    override fun execute(context: GuideExecutionContext): GuideNodeResult {
        return GuideNodeResult.Success
    }
}

/**
 * Нода завершения гайда
 */
class EndGuideNode(
    override val id: String,
    override val title: String = "End",
    override val description: String = "Конец гайда"
) : GuideNode() {
    
    override fun execute(context: GuideExecutionContext): GuideNodeResult {
        return GuideNodeResult.Success
    }
}

/**
 * Ноа-условие - проверяет некоторое состояние игры
 */
class ConditionGuideNode(
    override val id: String,
    override val title: String,
    override val description: String,
    val condition: (GuideExecutionContext) -> Boolean,
    val onSuccessNodeId: String? = null,
    val onFailureNodeId: String? = null
) : GuideNode() {
    
    override fun execute(context: GuideExecutionContext): GuideNodeResult {
        return if (condition(context)) {
            GuideNodeResult.Success
        } else {
            GuideNodeResult.Failed
        }
    }
}

/**
 * Ноа-действие - показывает подсказку или выполняет действие
 */
class ActionGuideNode(
    override val id: String,
    override val title: String,
    override val description: String,
    val actionType: ActionType,
    val message: String = "",
    val targetCellX: Int? = null,
    val targetCellY: Int? = null,
    val duration: Float = 0f // длительность показа в секундах
) : GuideNode() {
    
    private var elapsedTime: Float = 0f
    
    override fun initialize() {
        elapsedTime = 0f
    }
    
    override fun execute(context: GuideExecutionContext): GuideNodeResult {
        when (actionType) {
            ActionType.SHOW_MESSAGE -> {
                // Показываем сообщение игроку
                println("Guide Message: $message")
                return if (duration > 0) {
                    GuideNodeResult.Running
                } else {
                    GuideNodeResult.Success
                }
            }
            ActionType.HIGHLIGHT_CELL -> {
                // Подсвечиваем целевую клетку
                if (targetCellX != null && targetCellY != null) {
                    println("Highlighting cell at ($targetCellX, $targetCellY)")
                }
                return GuideNodeResult.Success
            }
            ActionType.WAIT_FOR_INPUT -> {
                // Ждем действия от игрока
                return GuideNodeResult.Running
            }
            ActionType.PLAY_ANIMATION -> {
                // Проигрываем анимацию
                return GuideNodeResult.Running
            }
        }
    }
    
    override fun reset() {
        super.reset()
        elapsedTime = 0f
    }
}

enum class ActionType {
    SHOW_MESSAGE,
    HIGHLIGHT_CELL,
    WAIT_FOR_INPUT,
    PLAY_ANIMATION
}

/**
 * Ноа-триггер - активируется при определенном событии
 */
class TriggerGuideNode(
    override val id: String,
    override val title: String,
    override val description: String,
    val triggerType: TriggerType,
    val triggerCondition: (GuideExecutionContext) -> Boolean
) : GuideNode() {
    
    private var wasTriggered: Boolean = false
    
    override fun execute(context: GuideExecutionContext): GuideNodeResult {
        if (triggerCondition(context)) {
            wasTriggered = true
            return GuideNodeResult.Success
        }
        
        return if (wasTriggered) {
            GuideNodeResult.Success
        } else {
            GuideNodeResult.Running
        }
    }
    
    override fun reset() {
        super.reset()
        wasTriggered = false
    }
}

enum class TriggerType {
    ON_TICK,
    ON_CELL_ADDED,
    ON_CELL_REMOVED,
    ON_RESOURCE_COLLECTED,
    ON_LEVEL_START,
    ON_CUSTOM_EVENT
}

/**
 * Ноа-задержка - ожидает указанное время
 */
class DelayGuideNode(
    override val id: String,
    override val title: String = "Delay",
    override val description: String = "Ожидание",
    val delaySeconds: Float = 1.0f
) : GuideNode() {
    
    private var elapsedTime: Float = 0f
    
    override fun initialize() {
        elapsedTime = 0f
    }
    
    override fun execute(context: GuideExecutionContext): GuideNodeResult {
        elapsedTime += 0.016f // примерно 1 кадр при 60 FPS
        
        return if (elapsedTime >= delaySeconds) {
            GuideNodeResult.Success
        } else {
            GuideNodeResult.Running
        }
    }
    
    override fun reset() {
        super.reset()
        elapsedTime = 0f
    }
}

/**
 * Ноа-ветвление - создает ветви в логике гайда
 */
class BranchGuideNode(
    override val id: String,
    override val title: String,
    override val description: String,
    val branches: List<Branch>
) : GuideNode() {
    
    data class Branch(
        val condition: (GuideExecutionContext) -> Boolean,
        val targetNodeId: String,
        val label: String = ""
    )
    
    override fun execute(context: GuideExecutionContext): GuideNodeResult {
        // Ветвление выбирает нужный путь на основе условий
        for (branch in branches) {
            if (branch.condition(context)) {
                return GuideNodeResult.Success
            }
        }
        
        // Если ни одно условие не выполнено, используем ветку по умолчанию (первую)
        return if (branches.isNotEmpty()) {
            GuideNodeResult.Success
        } else {
            GuideNodeResult.Failed
        }
    }
}

/**
 * Ноа для проверки наличия клетки определенного типа у игрока
 */
class CheckCellTypeNode(
    override val id: String,
    val cellType: String,
    val minCount: Int = 1,
    override val title: String = "Check Cell Type: $cellType",
    override val description: String = "Проверяет наличие клеток типа $cellType"
) : GuideNode() {
    
    override fun execute(context: GuideExecutionContext): GuideNodeResult {
        val count = context.playerCells.count { it.type == cellType }
        return if (count >= minCount) {
            GuideNodeResult.Success
        } else {
            GuideNodeResult.Running
        }
    }
}

/**
 * Ноа для проверки достижения определенной позиции
 */
class CheckPositionNode(
    override val id: String,
    val targetX: Float,
    val targetY: Float,
    val tolerance: Float = 5.0f,
    override val title: String = "Check Position",
    override val description: String = "Проверяет достижение позиции ($targetX, $targetY)"
) : GuideNode() {
    
    override fun execute(context: GuideExecutionContext): GuideNodeResult {
        val playerCenterX = context.playerCells.averageOrNull { it.x } ?: 0f
        val playerCenterY = context.playerCells.averageOrNull { it.y } ?: 0f
        
        val distance = kotlin.math.sqrt(
            (playerCenterX - targetX).pow(2) + (playerCenterY - targetY).pow(2)
        )
        
        return if (distance <= tolerance) {
            GuideNodeResult.Success
        } else {
            GuideNodeResult.Running
        }
    }
    
    private fun Float.pow(power: Int): Float {
        return kotlin.math.pow(this.toDouble(), power.toDouble()).toFloat()
    }
}

/**
 * Ноа для проверки сбора ресурса
 */
class CheckResourceNode(
    override val id: String,
    val resourceType: String,
    val minAmount: Float,
    override val title: String = "Check Resource: $resourceType",
    override val description: String = "Проверяет наличие ресурса $resourceType в количестве $minAmount"
) : GuideNode() {
    
    override fun execute(context: GuideExecutionContext): GuideNodeResult {
        val amount = context.resources[resourceType] ?: 0f
        return if (amount >= minAmount) {
            GuideNodeResult.Success
        } else {
            GuideNodeResult.Running
        }
    }
}
