package io.github.some_example_name.old.editor.commands

import io.github.some_example_name.old.editor.system.RestartSimulation
import java.util.Stack

// Интерфейс Command
interface Command {
    val stage: Int
    fun execute()
    fun undo() // redo() можно реализовать как execute(), если применимо
}

// Менеджер команд
class CommandEditorStackManager() {
    private val undoStack = Stack<Command>()
    private val redoStack = Stack<Command>()
    private val MAX_COMMANDS = 32  // Лимит на количество команд в стеке
    lateinit var restartSimulation: RestartSimulation

    fun bind(restartSimulation: RestartSimulation) {
        this.restartSimulation = restartSimulation
    }

    fun executeCommand(command: Command) {
        command.execute()
        undoStack.push(command)
        if (undoStack.size > MAX_COMMANDS) {
            undoStack.removeElementAt(0)  // Удаляем самую старую команду
        }
        redoStack.clear()  // Прерываем redo-цепочку
        restartSimulation.restartSimulation()
    }

    fun undo() {
        if (!undoStack.isEmpty()) {
            val command = undoStack.pop()
            command.undo()
            redoStack.push(command)
            if (redoStack.size > MAX_COMMANDS) {
                redoStack.removeElementAt(0)  // Удаляем самую старую команду
            }
            restartSimulation.restartSimulation()
        }
    }

    fun redo() {
        if (!redoStack.isEmpty()) {
            val command = redoStack.pop()
            command.execute()
            undoStack.push(command)
            if (undoStack.size > MAX_COMMANDS) {
                undoStack.removeElementAt(0)  // Удаляем самую старую команду
            }
            restartSimulation.restartSimulation()
        }
    }
}
