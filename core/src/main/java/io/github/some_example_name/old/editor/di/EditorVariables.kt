package io.github.some_example_name.old.editor.di

interface EditorVariables {
    var currentTick: Int
    var lastTick: Int
    var grabbedCellIndex: Int
    var lastGrabbedCellX: Float
    var lastGrabbedCellY: Float
    var isRightClick: Boolean
    var isDruggingCamera: Boolean
}
