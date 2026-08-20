package io.github.some_example_name.old.systems.node

open class NodeData {
    open var finalNode: Boolean = false
    open var funcNode: Boolean = false
    open var eventNode: Boolean = false

    open val arguments = mutableMapOf<Any, Any>()
}
