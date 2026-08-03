package io.github.some_example_name.old.entities.samples

import io.github.some_example_name.old.systems.genomics.genome.CellAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class CellEntitySample ( //оставлю на будущее как пример, потом можно перенести весь код на DTO но для удобства можно пока что в самих entity сделать.
    @ProtoNumber(1) val maxAmount: Int,
    @ProtoNumber(2) val particleIndexes: IntArray,
    @ProtoNumber(3) val cellGenomeId: IntArray,
    @ProtoNumber(4) val organIndex: IntArray,
    @ProtoNumber(5) val parentIndex: IntArray,
    @ProtoNumber(6) val angleCos: FloatArray,
    @ProtoNumber(7) val angleSin: FloatArray,
    @ProtoNumber(8) val angleDirectedCos: FloatArray,
    @ProtoNumber(9) val angleDirectedSin: FloatArray,
    @ProtoNumber(10) val angleCompensationCos: FloatArray,
    @ProtoNumber(11) val angleCompensationSin: FloatArray,
    @ProtoNumber(12) val energyNecessaryToDivide: FloatArray,
    @ProtoNumber(13) val energyNecessaryToMutate: FloatArray,
    @ProtoNumber(14) val isDividedInThisStage: BooleanArray,
    @ProtoNumber(15) val isMutateInThisStage: BooleanArray,
    @ProtoNumber(16) val cellType: ByteArray,
    @ProtoNumber(17) val energy: FloatArray,
    @ProtoNumber(18) val maxEnergy: FloatArray,
    @ProtoNumber(19) val isNeural: BooleanArray,
    @ProtoNumber(20) val neuronImpulseInput: FloatArray,
    @ProtoNumber(21) val neuronImpulseOutput: FloatArray,
    @ProtoNumber(22) val isOnEdge: BooleanArray,
    @ProtoNumber(23) val degreeOfShortening: FloatArray,
    @ProtoNumber(24) val pheromoneType: IntArray,
    @ProtoNumber(25) val linkAmount: IntArray,
    @ProtoNumber(26) val command: ByteArray,
    @ProtoNumber(27) val neuralConnections: Map<Int, List<Int>>,
//    @ProtoNumber(28) val cellActions: List<CellActionEntry>
)
