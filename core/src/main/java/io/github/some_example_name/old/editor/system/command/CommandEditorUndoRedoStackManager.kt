package io.github.some_example_name.old.editor.system.command

import io.github.some_example_name.old.editor.undo_redo_commands.UndoRedoCommand
import io.github.some_example_name.old.editor.system.logic.RestartSimulationCallBack
import java.util.Stack

class CommandEditorStackManager() {
    val undoStack = Stack<UndoRedoCommand>()
    val redoStack = Stack<UndoRedoCommand>()
    private val MAX_COMMANDS = 32
    lateinit var restartSimulationCallBack: RestartSimulationCallBack

    fun bind(restartSimulationCallBack: RestartSimulationCallBack) {
        this.restartSimulationCallBack = restartSimulationCallBack
    }

    fun executeCommand(command: UndoRedoCommand) {
        command.redo()
        undoStack.push(command)
        if (undoStack.size > MAX_COMMANDS) {
            undoStack.removeElementAt(0)
        }
        redoStack.clear()
        restartSimulationCallBack.restartSimulation()
    }

    fun undo() {
        if (!undoStack.isEmpty()) {
            val command = undoStack.pop()
            // Геном заменили мимо истории (открыли другой) — эта история к нему не относится.
            if (!command.undo()) {
                clear()
                return
            }
            redoStack.push(command)
            if (redoStack.size > MAX_COMMANDS) {
                redoStack.removeElementAt(0)
            }
            restartSimulationCallBack.restartSimulation()
        }
    }

    fun redo() {
        if (!redoStack.isEmpty()) {
            val command = redoStack.pop()
            if (!command.redo()) {
                clear()
                return
            }
            undoStack.push(command)
            if (undoStack.size > MAX_COMMANDS) {
                undoStack.removeElementAt(0)
            }
            restartSimulationCallBack.restartSimulation()
        }
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
