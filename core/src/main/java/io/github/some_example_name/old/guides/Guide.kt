package io.github.some_example_name.old.guides

/**
 * Гайд - коллекция нод, которые образуют сценарий обучения/подсказок.
 */
class Guide(
    val id: String,
    var title: String,
    var description: String,
    var nodes: MutableList<GuideNode> = mutableListOf(),
    var metadata: GuideMetadata = GuideMetadata()
) {
    /** Добавить ноду в гайд */
    fun addNode(node: GuideNode) {
        nodes.add(node)
    }
    
    /** Удалить ноду из гайда */
    fun removeNode(nodeId: String): Boolean {
        val node = nodes.find { it.id == nodeId } ?: return false
        nodes.remove(node)
        
        // Удаляем все связи с этой нодой
        nodes.forEach { n ->
            n.inputs.removeAll { it.fromNode.id == nodeId }
            n.outputs.removeAll { it.toNode.id == nodeId }
        }
        
        return true
    }
    
    /** Создать связь между двумя нодами */
    fun connectNodes(fromNodeId: String, toNodeId: String): Boolean {
        val fromNode = nodes.find { it.id == fromNodeId } ?: return false
        val toNode = nodes.find { it.id == toNodeId } ?: return false
        
        val connection = GuideNodeConnection(fromNode, toNode)
        fromNode.addOutput(connection)
        toNode.addInput(connection)
        
        return true
    }
    
    /** Удалить связь между двумя нодами */
    fun disconnectNodes(fromNodeId: String, toNodeId: String): Boolean {
        val fromNode = nodes.find { it.id == fromNodeId } ?: return false
        val toNode = nodes.find { it.id == toNodeId } ?: return false
        
        val connectionToRemove = fromNode.outputs.find { it.toNode.id == toNodeId }
        if (connectionToRemove != null) {
            fromNode.outputs.remove(connectionToRemove)
            toNode.inputs.removeAll { it.fromNode.id == fromNodeId }
            return true
        }
        
        return false
    }
    
    /** Получить все связи в гайде */
    fun getAllConnections(): List<GuideNodeConnection> {
        return nodes.flatMap { it.outputs }
    }
    
    /** Валидировать структуру гайда */
    fun validate(): GuideValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Проверка на наличие стартовой ноды
        val hasStartNode = nodes.any { it is StartGuideNode }
        if (!hasStartNode) {
            errors.add("Guide must have a StartGuideNode")
        }
        
        // Проверка на наличие конечной ноды
        val hasEndNode = nodes.any { it is EndGuideNode }
        if (!hasEndNode) {
            warnings.add("Guide should have an EndGuideNode")
        }
        
        // Проверка на изолированные ноды
        nodes.forEach { node ->
            if (node !is StartGuideNode && node.inputs.isEmpty()) {
                warnings.add("Node ${node.id} has no inputs and is not a start node")
            }
            if (node !is EndGuideNode && node.outputs.isEmpty()) {
                warnings.add("Node ${node.id} has no outputs and is not an end node")
            }
        }
        
        // Проверка на циклы
        if (hasCycle()) {
            warnings.add("Guide contains cycles which may cause infinite loops")
        }
        
        return GuideValidationResult(errors, warnings)
    }
    
    /** Проверка на наличие циклов в графе нод */
    private fun hasCycle(): Boolean {
        val visited = mutableSetOf<String>()
        val recStack = mutableSetOf<String>()
        
        fun dfs(nodeId: String): Boolean {
            visited.add(nodeId)
            recStack.add(nodeId)
            
            val node = nodes.find { it.id == nodeId } ?: return false
            
            for (output in node.outputs) {
                val nextNodeId = output.toNode.id
                if (!visited.contains(nextNodeId)) {
                    if (dfs(nextNodeId)) return true
                } else if (recStack.contains(nextNodeId)) {
                    return true
                }
            }
            
            recStack.remove(nodeId)
            return false
        }
        
        for (node in nodes) {
            if (!visited.contains(node.id)) {
                if (dfs(node.id)) return true
            }
        }
        
        return false
    }
}

/**
 * Метаданные гайда
 */
data class GuideMetadata(
    val author: String = "",
    val version: String = "1.0",
    val createdDate: Long = System.currentTimeMillis(),
    val modifiedDate: Long = System.currentTimeMillis(),
    val difficulty: GuideDifficulty = GuideDifficulty.EASY,
    val estimatedDuration: Int = 0, // в минутах
    val tags: List<String> = emptyList(),
    val targetLevel: String? = null // ID уровня, для которого предназначен гайд
)

/**
 * Уровни сложности гайда
 */
enum class GuideDifficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT
}

/**
 * Результат валидации гайда
 */
data class GuideValidationResult(
    val errors: List<String>,
    val warnings: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}
