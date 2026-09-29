package io.github.some_example_name.old.systems.logic

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import io.github.some_example_name.old.systems.logic.commands.Command
import io.github.some_example_name.old.systems.logic.commands.CommandInfo
import io.github.some_example_name.old.systems.logic.commands.Log

class Parser {
    var data: JsonValue? = null

    fun parseJSON(text: String): Boolean {
        try {
            data = JsonReader().parse(text)
            return true
        }
        catch (e: Exception) {
            println(e)
            return false
        }
    }

    fun checkJSON(text: String): Boolean {
        try {
            var valid = parseJSON(text)

            if (data == null) {
                return false
            }

            if (!valid) {
                return false
            }

            for (command in data) {
                println(command.name)
                val rootCommand = CommandsRegistry.getCommand(command.name)
                println(rootCommand)
                if (rootCommand != null) {
                    var allArgs = true
                    for (arg in rootCommand.args.keys) {
                        allArgs = command.has(arg)
                        if (!allArgs) {
                            break
                        }
                    }
                    valid = allArgs
                    if (!allArgs) {
                        break
                    }
                }
            }

            val commands = parse() //работает
            for (comm in commands) {
                println(comm)
                comm.execute(0)
            }
            return valid
        }
        catch (e: Exception) {
            return false
        }
    }

    fun parse(): List<Command> {
        if (data == null) {
            return listOf()
        }

        val commandList = mutableListOf<Command>()

        for (protoCommand in data) {
            val rootCommand: CommandInfo<*>? = CommandsRegistry.getCommand(protoCommand.name)

            val args = mutableMapOf<String, Any>()

            for (arg in protoCommand) {
                args[arg.name] = protoCommand[arg.name].asString()
            }

            val command = rootCommand?.create(args) as Command

            commandList.addLast(command)
        }

        return commandList
    }
}
