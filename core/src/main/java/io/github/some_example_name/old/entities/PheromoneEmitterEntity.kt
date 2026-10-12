package io.github.some_example_name.old.entities

class PheromoneEmitterEntity(
    pheromoneEmitterStartMaxAmount: Int
): Entity(pheromoneEmitterStartMaxAmount) {

    var lastImpulse = FloatArray(maxAmount)

    fun addPheromoneEmitter(): Int {
        val pheromoneEmitterIndex = add()
        this.lastImpulse[pheromoneEmitterIndex] = 0f
        return pheromoneEmitterIndex
    }

    fun deletePheromoneEmitter(pheromoneEmitterIndex: Int) {
        delete(pheromoneEmitterIndex)
        this.lastImpulse[pheromoneEmitterIndex] = 0f
    }

    override fun persistFields(state: EntityState) {
        state.floats(::lastImpulse)
    }

    override fun onCopy() {

    }

    override fun onPaste() {

    }

    override fun onClear(bound: Int) {
        lastImpulse.clear()
    }

    override fun onResize(oldMax: Int) {
        lastImpulse = lastImpulse.resize()
    }
}

