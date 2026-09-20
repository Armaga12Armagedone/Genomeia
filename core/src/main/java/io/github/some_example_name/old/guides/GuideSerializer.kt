package io.github.some_example_name.old.guides

/**
 * Сериализация/десериализация гайдов в JSON формат.
 * Использует простой формат для хранения и обмена гайдами.
 */
object GuideSerializer {
    
    /**
     * Сериализовать гайд в JSON строку
     */
    fun toJson(guide: Guide): String {
        val nodesJson = guide.nodes.joinToString(",\n    ") { node ->
            serializeNode(node)
        }
        
        val connectionsJson = guide.getAllConnections().joinToString(",\n    ") { conn ->
            """{"from": "${conn.fromNode.id}", "to": "${conn.toNode.id}"}"""
        }
        
        return """
{
    "id": "${guide.id}",
    "title": "${escapeJson(guide.title)}",
    "description": "${escapeJson(guide.description)}",
    "metadata": {
        "author": "${escapeJson(guide.metadata.author)}",
        "version": "${guide.metadata.version}",
        "createdDate": ${guide.metadata.createdDate},
        "modifiedDate": ${guide.metadata.modifiedDate},
        "difficulty": "${guide.metadata.difficulty}",
        "estimatedDuration": ${guide.metadata.estimatedDuration},
        "tags": [${guide.metadata.tags.joinToString(", ") { "\"$it\"" }}],
        "targetLevel": ${guide.metadata.targetLevel?.let { "\"$it\"" } ?: "null"}
    },
    "nodes": [
    $nodesJson
    ],
    "connections": [
    $connectionsJson
    ]
}
""".trimIndent()
    }
    
    /**
     * Десериализовать гайд из JSON строки
     */
    fun fromJson(json: String): Guide {
        // Простая реализация парсинга - в реальном проекте лучше использовать библиотеку типа Gson/Kotlinx Serialization
        val guideId = extractJsonValue(json, "id")
        val title = extractJsonValue(json, "title")
        val description = extractJsonValue(json, "description")
        
        val guide = Guide(
            id = unescapeJson(guideId),
            title = unescapeJson(title),
            description = unescapeJson(description)
        )
        
        // Парсим ноды (упрощенно)
        // В полной реализации здесь должен быть полноценный JSON парсер
        
        return guide
    }
    
    /**
     * Сериализовать отдельную ноду
     */
    private fun serializeNode(node: GuideNode): String {
        return when (node) {
            is StartGuideNode -> """
{
    "type": "START",
    "id": "${node.id}",
    "title": "${escapeJson(node.title)}",
    "description": "${escapeJson(node.description)}",
    "editorX": ${node.editorX},
    "editorY": ${node.editorY}
}""".trimIndent()
            
            is EndGuideNode -> """
{
    "type": "END",
    "id": "${node.id}",
    "title": "${escapeJson(node.title)}",
    "description": "${escapeJson(node.description)}",
    "editorX": ${node.editorX},
    "editorY": ${node.editorY}
}""".trimIndent()
            
            is ActionGuideNode -> """
{
    "type": "ACTION",
    "id": "${node.id}",
    "title": "${escapeJson(node.title)}",
    "description": "${escapeJson(node.description)}",
    "actionType": "${node.actionType}",
    "message": "${escapeJson(node.message)}",
    "targetCellX": ${node.targetCellX ?: "null"},
    "targetCellY": ${node.targetCellY ?: "null"},
    "duration": ${node.duration},
    "editorX": ${node.editorX},
    "editorY": ${node.editorY}
}""".trimIndent()
            
            is ConditionGuideNode -> """
{
    "type": "CONDITION",
    "id": "${node.id}",
    "title": "${escapeJson(node.title)}",
    "description": "${escapeJson(node.description)}",
    "editorX": ${node.editorX},
    "editorY": ${node.editorY}
}""".trimIndent()
            
            is DelayGuideNode -> """
{
    "type": "DELAY",
    "id": "${node.id}",
    "title": "${escapeJson(node.title)}",
    "description": "${escapeJson(node.description)}",
    "delaySeconds": ${node.delaySeconds},
    "editorX": ${node.editorX},
    "editorY": ${node.editorY}
}""".trimIndent()
            
            is CheckCellTypeNode -> """
{
    "type": "CHECK_CELL_TYPE",
    "id": "${node.id}",
    "cellType": "${node.cellType}",
    "minCount": ${node.minCount},
    "title": "${escapeJson(node.title)}",
    "description": "${escapeJson(node.description)}",
    "editorX": ${node.editorX},
    "editorY": ${node.editorY}
}""".trimIndent()
            
            is CheckPositionNode -> """
{
    "type": "CHECK_POSITION",
    "id": "${node.id}",
    "targetX": ${node.targetX},
    "targetY": ${node.targetY},
    "tolerance": ${node.tolerance},
    "title": "${escapeJson(node.title)}",
    "description": "${escapeJson(node.description)}",
    "editorX": ${node.editorX},
    "editorY": ${node.editorY}
}""".trimIndent()
            
            is CheckResourceNode -> """
{
    "type": "CHECK_RESOURCE",
    "id": "${node.id}",
    "resourceType": "${node.resourceType}",
    "minAmount": ${node.minAmount},
    "title": "${escapeJson(node.title)}",
    "description": "${escapeJson(node.description)}",
    "editorX": ${node.editorX},
    "editorY": ${node.editorY}
}""".trimIndent()
            
            else -> """
{
    "type": "CUSTOM",
    "id": "${node.id}",
    "title": "${escapeJson(node.title)}",
    "description": "${escapeJson(node.description)}",
    "editorX": ${node.editorX},
    "editorY": ${node.editorY}
}""".trimIndent()
        }
    }
    
    /**
     * Экранирование специальных символов в JSON
     */
    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    
    /**
     * Деэкранирование специальных символов в JSON
     */
    private fun unescapeJson(str: String): String {
        return str
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
    
    /**
     * Простое извлечение значения из JSON по ключу
     */
    private fun extractJsonValue(json: String, key: String): String {
        val pattern = """"$key"\s*:\s*"([^"]*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1) ?: ""
    }
}
