package io.github.some_example_name.old.systems.logic.langugaeUtils

import kotlin.math.pow

class Evaulator { //выполняет код
    val varaibles = mutableMapOf<String, Any>()

    fun run(node: ASTNode): Any {
        return when(node) {
            is ASTNode.Number -> node.value
            is ASTNode.Variable -> varaibles[node.name] ?: throw Exception("Переменная '${node.name}' не определена")
            is ASTNode.UnaryOp -> {
                val operand = run(node.operand) //Рекурсия, тяжело.. Как я понял это нужно что бы получить значение числа.
                when (node.operator) {
                    "-" -> -toDouble(operand)
                    "!" -> !toBoolean(operand)
                    else -> throw Exception("Нет оператора числа: ${node.operator}")
                }
            }
            is ASTNode.BinaryOp -> {
                val left = run(node.left)
                val leftDouble = toDouble(left)
                val leftBool = toBoolean(left)

                when(node.operator) {
                    //логика
                    "&&" -> {
                        if (!leftBool) return false
                        toBoolean(run(node.right))
                    }
                    "||" -> {
                        if (leftBool) return true
                        toBoolean(run(node.right))
                    }

                    else -> {
                        val right = run(node.right)
                        val rightDouble = toDouble(right)
                        when (node.operator) {
                            "+" -> leftDouble + rightDouble
                            "-" -> leftDouble - rightDouble
                            "*" -> leftDouble * rightDouble
                            "/" -> {
                                val r = rightDouble
                                if (r != 0.toDouble()) {
                                    leftDouble / r
                                }
                                else {
                                    throw SyntaxException("На ноль делить нельзя!")
                                }
                            }
                            "**" -> leftDouble.pow(rightDouble)

                            //сравнения
                            "==" -> leftDouble == rightDouble
                            ">" -> leftDouble > rightDouble
                            "<" -> leftDouble < rightDouble
                            ">=" -> leftDouble >= rightDouble
                            "<=" -> leftDouble <= rightDouble
                            "!=" -> leftDouble != rightDouble
                            else -> throw SyntaxException("Нет такого бинарного оператора!")
                        }
                    }
                }
            }
        }
    }

    private fun toDouble(value: Any): Double = when (value) {
        is Double -> value
        is Boolean -> if (value) 1.0 else 0.0
        else -> throw Exception("Нельзя преобразовать в число: $value")
    }

    private fun toBoolean(value: Any): Boolean = when (value) {
        is Boolean -> value
        is Double -> value != 0.0
        else -> throw Exception("Нельзя преобразовать в булево: $value")
    }
}
