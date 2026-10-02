package io.github.some_example_name.old.systems.logic.commands

data class Condition(val condText: String, val rightCond: HashMap<Int, String>, val falseCond: HashMap<Int, String>): Command {
    override val args = mutableMapOf<String, Any>()
    override val name = "Log"
    override val type = 0
    override var id = -1

    var conditionText: String by args
    var rightCondition: HashMap<Int, String> by args
    var falseCondition: HashMap<Int, String> by args

    init {
        conditionText = condText
        rightCondition = rightCond
        falseCondition = falseCond
    }

    override fun execute(worldContext: Int) {
        println(args["text"])
    }

    companion object info: CommandInfo<Log> {
        override val name = "Log"

        override val args: Map<String, Any> = mapOf("text" to String)

        override fun create(datas: Map<String, Any>): Log {
            return Log(datas["text"].toString())
        }
    }
}
