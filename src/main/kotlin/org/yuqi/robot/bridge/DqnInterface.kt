package org.yuqi.robot.bridge

import org.yuqi.nn.NeuralNetwork
import org.yuqi.nn.math.ActivationFunctions
import org.yuqi.nn.math.IActivationFunctionPair
import org.yuqi.util.IQTable
import org.yuqi.util.discard
import org.yuqi.util.normalizeRadian
import java.io.File
import kotlin.math.min

class DqnInterface(val nn: NeuralNetwork) : IQTable<State, Action> {
    companion object {
        fun toInputs(state: State, action: Action): FloatArray {
            val inputs = Array(15) { 0.0 }
            with(state) {
                inputs[0] = status.energy
                inputs[1] = status.velocity
                val wallDist = distanceToWalls()
                inputs[2] = min(wallDist.up, wallDist.down)
                inputs[3] = min(wallDist.left, wallDist.right)
                inputs[4] = opponent.energy
                inputs[5] = normalizeRadian(opponent.heading - status.heading)
                inputs[6] = state.opponent.velocity
                val opWallDist = distanceToWalls(opponent.position)
                inputs[7] = min(opWallDist.up, opWallDist.down)
                inputs[8] = min(opWallDist.left, opWallDist.right)
                inputs[9] = bearing.length
                inputs[10] = normalizeRadian(bearing.radian - status.heading)
                inputs[11] = status.gunHeat
            }
            with(action) {
                inputs[12] = if (component == ActionComponent.Engine) value else 0.0
                inputs[13] = if (component == ActionComponent.SteeringGear) value else 0.0
                inputs[14] = if (component == ActionComponent.Trigger) value else 0.0
            }
            return inputs.map { it.toFloat() }.toFloatArray()
        }
    }

    constructor(file: File, compressed: Boolean = true) : this(NeuralNetwork.load(file, compressed))
    constructor(hiddenLayerConfigs: Iterable<Pair<Int, IActivationFunctionPair>>) :
        this(NeuralNetwork(15, 1, hiddenLayerConfigs.map { it.first }, hiddenLayerConfigs.map { it.second } + ActivationFunctions.LINEAR))

    override val actions: Iterable<Action> get() = Action.entries

    override fun get(state: State, action: Action): Float = nn.predict(toInputs(state, action)).single()
    override fun set(state: State, action: Action, value: Float) = nn.train(toInputs(state, action), floatArrayOf(value)).discard()
}