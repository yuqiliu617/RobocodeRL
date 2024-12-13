package org.yuqi.robot.bridge

import org.yuqi.nn.NeuralNetwork
import org.yuqi.nn.math.IActivationFunctionPair
import java.io.File

class DqnTrainingInterface : DqnInterface {
    val qTable: LocalQTable

    constructor(nn: NeuralNetwork, file: File, qTable: LocalQTable) : super(nn, file) {
        this.qTable = qTable
    }
    constructor(file: File, qTable: LocalQTable) : super(file) {
        this.qTable = qTable
    }
    constructor(hiddenLayerConfigs: Iterable<Pair<Int, IActivationFunctionPair>>, file: File, qTable: LocalQTable) : super(hiddenLayerConfigs, file) {
        this.qTable = qTable
    }

    override fun get(state: State, action: Action): Float = qTable[state, action]
}