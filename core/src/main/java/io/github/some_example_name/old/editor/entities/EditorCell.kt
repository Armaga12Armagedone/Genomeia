package io.github.some_example_name.old.editor.entities

import io.github.some_example_name.old.systems.genomics.genome.Action

data class EditorCell(
    val id: Int,
    val index: Int,
    val parentIndex: Int,
    val parentId: Int,
    var x: Float,
    var y: Float,
    val radius: Float,
    val angleToParent: Float,
    val angleDirected: Float?,
    val isPhantom: Boolean,
    val divide: Action?,
    val mutate: Action?,
    val actual: Action?
)
