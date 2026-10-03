package io.github.some_example_name.old.systems.logic.langugaeUtils

class Lexer { //поменять object на class

    init {
        println("work")
        val lexes = lexer("24>=0.1 || 3>1 && (varab < 0 || 0+1>virib)")
        ASTBuilder().buildAST(lexes)
    }

    fun lexer(text: String): List<Lex> { //по идее должен превращать входные параметры в полноценные выполняемые выражения.
        val lexs = mutableListOf<Lex>()
        var index = 0
        println(text.length)
        while (index < text.length){
            println("index: " + index)
            val miniLex = text[index]
            println(miniLex)
            if (miniLex.isDigit()) {
                var pos = index
                var hasDot = false
                var totalVar = ""
                while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) {
                    println("pos: " + pos)
                    println("var: " + text[pos])
                    if (text[pos] == '.') {
                        if (hasDot) {
                            println("nuh uh")
                            break
                        }

                        hasDot = true
                    }

                    totalVar += text[pos]

                    pos +=1
                }

                index = pos-1 //если не работает то idnex += (pos-index)-1
                lexs.addLast(Lex(totalVar, type=0, intType = if (hasDot) 2 else 1, 0))

            }

            if (miniLex in "=><+-/*!&|") {
                if (index+1<text.length) {
                    val pairChars = text.slice(index..index + 1)

                    if (pairChars in listOf(">=", "<=", "==", "*=", "+=", "-=", "/=", "!=", "&&", "||")) {
                        println("it's pair")
                        println(pairChars)
                        index += 1

                        lexs.addLast(Lex(pairChars, type=4, intType = 0, order = 0))//потом определим им order'а
                    }
                    else {
                        println("one operator")

                        val isEqualOperator = if (miniLex in "><") 4 else 1

                        lexs.addLast(Lex(miniLex.toString(), type=isEqualOperator, intType = 0, order = 0))//определить
                    }

                }
                else {
                    val isEqualOperator = if (miniLex in "><") 4 else 1
                    lexs.addLast(Lex(miniLex.toString(), type=isEqualOperator, intType = 0, order = 0))
                }
            }

            if (miniLex in "()") {
                lexs.addLast(Lex(lexChar = miniLex.toString(), type=6, intType = 0, order = 0))
            }

            if (miniLex.isLetter()) {
                var totalVar = ""

                var pos = index

                while (pos<text.length && (text[pos].isLetter() || text[pos].isDigit() )) {
                    totalVar += text[pos]

                    pos += 1
                }

                index = pos-1
                lexs.addLast(Lex(totalVar,2, 0, 0))
            }


            index += 1
        }

        println(lexs)

        return lexs
    }
}

data class Lex(
    val lexChar: String,
    val type: Int, //0-int, 1-знак(+-*/ и тд), 2-переменная 3-строка 4-оператор выражения: And, Or, Not, Equals, 5-изменятель числа !, -; 6-скобки
    val intType: Int, //0-не Int, 1-Int, 2-Float
    val order: Int //важность, чем выше тем больше приоритет выполнения
)
