package io.github.some_example_name.old.cells.base

import io.github.some_example_name.old.cells.*
import io.github.some_example_name.old.core.DIContext
import io.github.some_example_name.old.core.DISimulationContainer.threadCount
import io.github.some_example_name.old.core.WorldResizable
import java.util.BitSet

class CellListBuilder: WorldResizable {

    lateinit var context: DIContext

    val zygote = Zygote(18)
    val eye = Eye(14)

    val instances = listOf(
        Leaf(0),
        Fat(1),
        Bone(2),
        Tail(3),
        Neuron(4),
        Muscle(5),
        FoodSensor(6),
        Sucker(7),
        Stem(8),
        Excreta(9),
        SuctionCup(10),
        Sticky(11),
        Pumper(12),
        Chameleon(13),
        eye,
        Compass(15),
        Synapse(16),
        TouchTrigger(17),
        zygote,
        Producer(19),
        Breakaway(20),
        Fin(21),
        PheromoneEmitter(22),
        PheromoneSensor(23),
        Punisher(24),
        SpeedSensor(25)
    ).sortedBy { it.cellTypeId }

    override fun resize() {
        eye.visitedBits = Array(threadCount) { BitSet(context.gridManager.gridSize) }
        eye.checkedObjectListId = Array(threadCount) { IntArray(16) { -1 } }
    }

    fun bindToDIContext(context: DIContext) {
        this.context = context
        instances.forEach {
            if (it is Eye) {
                it.visitedBits = Array(threadCount) { BitSet(context.gridManager.gridSize) }
                it.checkedObjectListId = Array(threadCount) { IntArray(16) { -1 } }
            }
            it.context = context
        }
    }

}
