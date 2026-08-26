package io.github.some_example_name.old.systems.node

class Context {
    val variables = mutableMapOf<String, Value>()
}

sealed class Value {
    data class StringVal(val data: String) : Value()
    data class IntVal(val data: Int) : Value()
    data class BoolVal(val data: Boolean) : Value()
}
