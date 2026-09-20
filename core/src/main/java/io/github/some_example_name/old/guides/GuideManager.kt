package io.github.some_example_name.old.guides

/**
 * Менеджер для управления системой гайдов.
 * Отвечает за загрузку, сохранение и выполнение гайдов.
 */
class GuideManager {
    
    /** Список всех загруженных гайдов */
    private val guides: MutableMap<String, Guide> = mutableMapOf()
    
    /** Текущий активный гайд */
    private var currentGuide: Guide? = null
    
    /** Контекст выполнения текущего гайда */
    private var executionContext: GuideExecutionContext? = null
    
    /** Загрузить гайд из JSON строки */
    fun loadGuide(json: String): Guide? {
        return try {
            val guide = GuideSerializer.fromJson(json)
            guides[guide.id] = guide
            guide
        } catch (e: Exception) {
            println("Error loading guide: ${e.message}")
            null
        }
    }
    
    /** Сохранить гайд в JSON строку */
    fun saveGuide(guide: Guide): String {
        return GuideSerializer.toJson(guide)
    }
    
    /** Начать выполнение гайда */
    fun startGuide(guideId: String, context: GuideExecutionContext): Boolean {
        val guide = guides[guideId] ?: return false
        
        currentGuide = guide
        executionContext = context
        
        // Инициализируем все ноды
        guide.nodes.forEach { it.initialize() }
        
        // Находим стартовую ноду и начинаем выполнение
        val startNode = guide.nodes.find { it is StartGuideNode }
        if (startNode != null) {
            executeNode(startNode)
        }
        
        return true
    }
    
    /** Остановить текущий гайд */
    fun stopGuide() {
        currentGuide?.nodes?.forEach { it.reset() }
        currentGuide = null
        executionContext = null
    }
    
    /** Обновить состояние текущего гайда */
    fun updateGuide(deltaTime: Float) {
        currentGuide?.let { guide ->
            executionContext?.let { context ->
                // Проверяем активные ноды
                val activeNodes = guide.nodes.filter { it.isActive }
                activeNodes.forEach { node ->
                    when (val result = node.execute(context)) {
                        is GuideNodeResult.Success -> {
                            node.isActive = false
                            node.result = result
                            // Активируем следующие ноды
                            activateNextNodes(node, guide)
                        }
                        is GuideNodeResult.Failed -> {
                            node.isActive = false
                            node.result = result
                            // Обработка неудачи
                            handleNodeFailure(node, guide)
                        }
                        is GuideNodeResult.Running -> {
                            // Нода все еще выполняется
                            node.result = result
                        }
                        is GuideNodeResult.Skipped -> {
                            node.isActive = false
                            node.result = result
                            activateNextNodes(node, guide)
                        }
                    }
                }
            }
        }
    }
    
    /** Активировать следующие ноды после успешного выполнения текущей */
    private fun activateNextNodes(currentNode: GuideNode, guide: Guide) {
        currentNode.outputs.forEach { connection ->
            val nextNode = connection.toNode
            // Проверяем, все ли входящие связи следующей ноды выполнены
            if (nextNode.inputs.all { input -> 
                    input.fromNode.result is GuideNodeResult.Success || 
                    input.fromNode.result is GuideNodeResult.Skipped
                }) {
                nextNode.isActive = true
                nextNode.initialize()
            }
        }
    }
    
    /** Обработка неудачи ноды */
    private fun handleNodeFailure(node: GuideNode, guide: Guide) {
        // Можно реализовать различную логику обработки неудач
        // Например, попробовать альтернативную ветку или завершить гайд
        println("Node ${node.id} failed")
    }
    
    /** Создать новый гайд */
    fun createGuide(id: String, title: String, description: String): Guide {
        val guide = Guide(
            id = id,
            title = title,
            description = description
        )
        guides[id] = guide
        return guide
    }
    
    /** Получить гайд по ID */
    fun getGuide(guideId: String): Guide? {
        return guides[guideId]
    }
    
    /** Удалить гайд */
    fun removeGuide(guideId: String): Boolean {
        if (guides.containsKey(guideId)) {
            guides.remove(guideId)
            if (currentGuide?.id == guideId) {
                stopGuide()
            }
            return true
        }
        return false
    }
    
    /** Получить список всех гайдов */
    fun getAllGuides(): List<Guide> {
        return guides.values.toList()
    }
    
    /** Проверить, активен ли гайд */
    fun isGuideActive(): Boolean {
        return currentGuide != null
    }
    
    /** Получить текущий активный гайд */
    fun getCurrentGuide(): Guide? {
        return currentGuide
    }
}
