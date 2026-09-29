package io.github.some_example_name.old.systems.logic

import io.github.some_example_name.old.systems.logic.commands.Command
import io.github.some_example_name.old.systems.logic.commands.CommandInfo
import io.github.some_example_name.old.systems.logic.commands.Log

object CommandsRegistry {
    val commandsRegistry = hashMapOf<String, CommandInfo<*>>()

    fun registerCommand(command: CommandInfo<*>) {
        commandsRegistry[command.name] = command
    }

    fun getCommand(text: String): CommandInfo<*>? { return commandsRegistry[text] }

    init {
        CommandsRegistry.registerCommand(Log)
    }
}
