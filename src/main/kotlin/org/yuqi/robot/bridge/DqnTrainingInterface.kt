package org.yuqi.robot.bridge

import org.yuqi.nn.NeuralNetwork
import org.yuqi.nn.math.IActivationFunctionPair
import java.io.File

class DqnTrainingInterface : DqnInterface {
    val qTable: LocalQTable
    val online = false

    constructor(nn: NeuralNetwork, file: File, qTable: LocalQTable) : super(nn, file) {
        this.qTable = qTable
    }

    constructor(file: File, qTable: LocalQTable) : super(file) {
        this.qTable = qTable
    }

    constructor(hiddenLayerConfigs: Iterable<Pair<Int, IActivationFunctionPair>>, file: File, qTable: LocalQTable) : super(hiddenLayerConfigs, file) {
        this.qTable = qTable
    }

    init {
        assert(training) { "Training must be enabled for DqnTrainingInterface" }
    }

    override fun get(state: State, action: Action): Float = qTable[state, action]
    override fun set(state: State, action: Action, value: Float) {
        val expected = qTable.getRow(state).map { it.second }.toFloatArray()
        if (online)
            expected[action.index] = value
        nn.train(state.toInputs(), expected)
    }
}