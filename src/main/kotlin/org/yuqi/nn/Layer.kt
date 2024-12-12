package org.yuqi.nn

import org.yuqi.nn.math.IActivationFunctionPair
import java.util.*

class Layer(val inputSize: Int, val outputSize: Int, val activate: IActivationFunctionPair) {
    val weightUpdates = Array(outputSize) { FloatArray(inputSize) { 0F } }
    val biasUpdates = FloatArray(outputSize) { 0F }

    val weights = Array(outputSize) { FloatArray(inputSize) { 0F } }
    val biases = FloatArray(outputSize) { 0F }
    val outputs = FloatArray(outputSize)

    fun reset() {
        weights.forEach { ws -> ws.fill(0F) }
        biases.fill(0F)
    }

    fun reset(range: OpenEndRange<Float>, random: Random? = null) {
        val rnd = random ?: Random()
        weights.forEach { ws ->
            for (i in ws.indices)
                ws[i] = range.start + rnd.nextFloat() * (range.endExclusive - range.start)
        }
        for (i in biases.indices)
            biases[i] = range.start + rnd.nextFloat() * (range.endExclusive - range.start)
    }

    fun feedForward(inputs: FloatArray): FloatArray {
        for (i in 0 until outputSize) {
            outputs[i] = biases[i] + inputs.zip(weights[i]).map { (input, weight) -> input * weight }.sum()
            outputs[i] = activate.f(outputs[i])
        }
        return outputs
    }
}