package io.github.some_example_name.old.systems.node

open class NodeData(
    open var finalNode: Boolean = false,
    open var funcNode: Boolean = false,
    open var eventNode: Boolean = false,
    open var argumentNode: Boolean = false,

    open val arguments: MutableMap<Any, Any> = mutableMapOf<Any, Any>()
) {

}
