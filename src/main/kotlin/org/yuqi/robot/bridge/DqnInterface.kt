package org.yuqi.robot.bridge

import org.yuqi.nn.NeuralNetwork
import org.yuqi.nn.math.ActivationFunctions
import org.yuqi.nn.math.IActivationFunctionPair
import org.yuqi.util.IPersistentQTable
import org.yuqi.util.normalizeRadian
import java.io.File
import kotlin.math.min

open class DqnInterface(nn: NeuralNetwork, val file: File) : IPersistentQTable<State, Action> {
    var nn: NeuralNetwork = nn
        private set
    var training = true

    constructor(file: File) : this(NeuralNetwork.load(file), file)
    constructor(hiddenLayerConfigs: Iterable<Pair<Int, IActivationFunctionPair>>, file: File) :
        this(NeuralNetwork(12, Action.maxIndex, hiddenLayerConfigs.map { it.first }, hiddenLayerConfigs.map { it.second } + ActivationFunctions.Identity), file)

    override val actions: Iterable<Action> get() = Action.entries

    override fun get(state: State, action: Action): Float = nn.predict(state.toInputs())[action.index]
    override fun set(state: State, action: Action, value: Float) {
        if (!training)
            return
        val expected = nn.predict(state.toInputs())
        expected[action.index] = value
        nn.train(state.toInputs(), expected)
    }

    override fun getRow(state: State): List<Pair<Action, Float>> {
        val outputs = nn.predict(state.toInputs())
        return actions.zip(outputs.asIterable()).toList()
    }

    override fun save() = nn.save(file)
    override fun load() {
        nn = NeuralNetwork.load(file)
    }
}

fun State.toInputs(): FloatArray {
    val inputs = DoubleArray(12)
    inputs[0] = status.energy
    inputs[1] = status.velocity
    val wallDist = distanceToWalls()
    inputs[2] = min(wallDist.up, wallDist.down)
    inputs[3] = min(wallDist.left, wallDist.right)
    inputs[4] = opponent.energy
    inputs[5] = normalizeRadian(opponent.heading - status.heading)
    inputs[6] = opponent.velocity
    val opWallDist = distanceToWalls(opponent.position)
    inputs[7] = min(opWallDist.up, opWallDist.down)
    inputs[8] = min(opWallDist.left, opWallDist.right)
    inputs[9] = bearing.length
    inputs[10] = normalizeRadian(bearing.radian - status.heading)
    inputs[11] = status.gunHeat
    return inputs.map { it.toFloat() }.toFloatArray()
}