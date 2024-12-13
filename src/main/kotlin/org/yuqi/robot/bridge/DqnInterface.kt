package org.yuqi.robot.bridge

import org.yuqi.nn.NeuralNetwork
import org.yuqi.nn.math.ActivationFunctions
import org.yuqi.nn.math.IActivationFunctionPair
import org.yuqi.util.IPersistentQTable
import org.yuqi.util.discard
import org.yuqi.util.normalizeRadian
import java.io.File
import kotlin.math.min

open class DqnInterface(nn: NeuralNetwork, val file: File) : IPersistentQTable<State, Action> {
    companion object {
        fun toInputs(state: State, action: Action): FloatArray {
            val stateInputs = state.toInputs()
            val actionInputs = action.toInputs()
            return stateInputs + actionInputs
        }
    }

    var nn: NeuralNetwork = nn
        private set

    constructor(file: File) : this(NeuralNetwork.load(file), file)
    constructor(hiddenLayerConfigs: Iterable<Pair<Int, IActivationFunctionPair>>, file: File) :
        this(NeuralNetwork(15, 1, hiddenLayerConfigs.map { it.first }, hiddenLayerConfigs.map { it.second } + ActivationFunctions.IDENTITY), file)

    override val actions: Iterable<Action> get() = Action.entries

    override fun get(state: State, action: Action): Float = nn.predict(toInputs(state, action)).single()
    override fun set(state: State, action: Action, value: Float) = nn.train(toInputs(state, action), floatArrayOf(value)).discard()

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

fun Action.toInputs(): FloatArray {
    val inputs = DoubleArray(3)
    inputs[0] = if (component == ActionComponent.Engine) value else 0.0
    inputs[1] = if (component == ActionComponent.SteeringGear) value else 0.0
    inputs[2] = if (component == ActionComponent.Trigger) value else 0.0
    return inputs.map { it.toFloat() }.toFloatArray()
}