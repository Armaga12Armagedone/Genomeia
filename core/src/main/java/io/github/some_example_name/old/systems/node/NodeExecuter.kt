package io.github.some_example_name.old.systems.node

import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.ActionNode
import io.github.some_example_name.old.features.levelEditor.nodes.actionNodes.OnStartAction

class NodeExecuter {
    private val nodeParser = NodeParser()
    private val context = Context()
    private var events = mutableMapOf<ActionNode, MutableList<ActionNode>>()

    fun run(nodes: MutableList<Node>) {
        events = nodeParser.parse(nodes)

        OnStartEvent()
//        for (event in events) {
//            println(event)
//            for (node in event.value) {
//                println(node)
//                node.execute(context)
//            }
//        }
    }

    fun OnStartEvent() {
        val startEvent = events.filter { it.key is OnStartAction }
        println(startEvent.values)
        if (startEvent.values.first().size > 0) {
//            for (node in startEvent.values.first()) {
//                node.execute(context)
//            }
            startEvent.values.first()[0].execute(context) //будет ка4к домино эффект
        }
    }

    //Слушай завтрашний я, тут дело не простое, нужно выбрать как делать правильно event, я думаю ты запомнил что делать коллизию со стеной плохая идея для производительность
    //Поэтому я думаю сделать проверку расстояния до определенной точки. А это как ты наверное предполагаешь требует реализации:
    //Условий, точки GOAL в редакторе карты, остлеживания координат клеток организма, и добавление ограничений. В общем тяжело.
}
