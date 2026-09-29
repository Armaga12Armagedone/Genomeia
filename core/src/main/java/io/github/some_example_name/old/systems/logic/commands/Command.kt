package io.github.some_example_name.old.systems.logic.commands

import com.badlogic.gdx.utils.JsonValue

interface Command {
    val args: Map<String, Any>
    val name: String

    val type: Int //0-command, 1-event

    var id: Int

    fun execute(worldContext: Int)

//    companion object info: CommandInfo<Command> {
//        override val name: String = ""
//        override val args: Map<String, Any> = mapOf()
//
//        override fun create(datas: Map<String, Any>): Command {
//            return Command
//        }
//    }
}

interface CommandInfo<C: Command> {
    val name: String
    val args: Map<String, Any>

    fun create(datas: Map<String, Any>): C
}
