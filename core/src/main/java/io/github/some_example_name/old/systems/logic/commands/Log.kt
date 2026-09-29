package io.github.some_example_name.old.systems.logic.commands

import io.github.some_example_name.old.systems.logic.CommandsRegistry

data class Log(var textVar: String): Command {
    override val args = mutableMapOf<String, Any>()
    override val name = "Log"
    override val type = 0
    override var id = -1

    var text: String by args

    init {
        text = textVar
    }

    override fun execute(worldContext: Int) {
        println(args["text"])
    }

    companion object info: CommandInfo<Log> {
        override val name = "Log"

        override val args: Map<String, Any> = mapOf("text" to String)

        override fun create(datas: Map<String, Any>): Log {
            return Log(datas["text"].toString())
        }
    }
}
