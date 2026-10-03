package io.github.some_example_name.old.systems.logic.langugaeUtils

import jdk.internal.org.jline.reader.SyntaxError

class ASTBuilder { //создает AST-древо, чем выше в дереве, тем ниже приоритет
    private var lexs = listOf<Lex>()
    private var id: Int = 0

    fun buildAST(lexes: List<Lex>): ASTNode {
        lexs = lexes
        id = 0
        val AST = findLogicOperatorOr()
        return AST
    }

    fun findLogicOperatorOr(): ASTNode { //самый низкий приоритет
        var left = findLogicOperatorAnd()

        while (id < lexs.size && lexs[id].type == 4 && lexs[id].lexChar == "||") {
            val op = consume(id)
            val right = findLogicOperatorAnd()

            left = ASTNode.BinaryOp(op.lexChar, left, right)
        }

        return left
    }

    fun findLogicOperatorAnd(): ASTNode { // средний приоритет
        var left = findLogicOperatorEquals()

        while (id < lexs.size && lexs[id].type == 4 && lexs[id].lexChar == "&&") {
            val op = consume(id)
            val right = findLogicOperatorEquals()

            left = ASTNode.BinaryOp(op.lexChar, left, right)
        }

        return left
    }

    fun findLogicOperatorEquals(): ASTNode { //высший приоритет
        var left = findOpAddSub()

        while (id < lexs.size && lexs[id].type == 4 && lexs[id].lexChar in listOf(">=", "<=", "==", "!=", "<", ">")) {
            val op = consume(id)
            val right = findOpAddSub()

            left = ASTNode.BinaryOp(op.lexChar, left, right)
        }

        return left
    }

    fun findOpAddSub(): ASTNode {
        var left = findOpMultDiv()

        while (id < lexs.size && lexs[id].type == 1 && lexs[id].lexChar in "+-") {
            val op = consume(id)
            val right = findOpMultDiv()

            left = ASTNode.BinaryOp(op.lexChar, left, right)
        }

        return left
    }

    fun findOpMultDiv(): ASTNode {
        var left = findOpPow()

        while (id < lexs.size && lexs[id].type == 1 && lexs[id].lexChar in "*/") {
            val op = consume(id)
            val right = findOpPow()

            left = ASTNode.BinaryOp(op.lexChar, left, right)
        }

        return left
    }

    fun findOpPow(): ASTNode {
        var base = findNumChanger()

        if (id < lexs.size && lexs[id].type == 1 && lexs[id].lexChar == "**") {
            val op = consume(id)

            val exp = findOpPow()
            return ASTNode.BinaryOp(op.lexChar, base, exp)
        }

        return base
    }

    fun findNumChanger(): ASTNode { //ищет ! и -
        if (id < lexs.size && lexs[id].lexChar in listOf("-", "!")) {
            val op = consume(id)
            val operand = findNumChanger()  // ← рекурсия в саму себя!
            return ASTNode.UnaryOp(op.lexChar, operand)
        }
        return parseNums()
    }

    fun parseNums(): ASTNode { // ищет числа и переменные
        if (id < lexs.size && (lexs[id].type == 0 || lexs[id].type == 2)) {
            val numVar = consume(id)
            return if (numVar.type == 0) ASTNode.Number(numVar.lexChar.toDouble()) else ASTNode.Variable(numVar.lexChar)
        }
        if (id < lexs.size && lexs[id].type == 6 && lexs[id].lexChar == "(") {
            val skobka = consume(id)

            var result = findLogicOperatorOr()

            if (id >= lexs.size || lexs[id].lexChar != ")") {
                throw SyntaxException("Скобка не закрыта")
            }

            consume(id)  // съедаем ")"
            return result
        }

        throw SyntaxException("nuh uh, there no num!")
    }

    fun consume(lexId: Int): Lex {
        val lex = lexs[lexId]
        id++
        return lex
    }
}

sealed class ASTNode {
    // Листья - 0 детей
    data class Number(val value: Double) : ASTNode()
    data class Variable(val name: String) : ASTNode()

    // Унарные - 1 ребёнок
    data class UnaryOp(
        val operator: String,
        val operand: ASTNode
    ) : ASTNode()

    // Бинарные - 2 ребёнка
    data class BinaryOp(
        val operator: String,
        val left: ASTNode,
        val right: ASTNode
    ) : ASTNode()
}

class SyntaxException(error: String): Exception(error)
