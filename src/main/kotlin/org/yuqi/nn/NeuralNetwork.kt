@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package org.yuqi.nn

import org.yuqi.nn.math.ActivationFunctions
import org.yuqi.nn.math.ErrorFunctions
import org.yuqi.nn.math.IActivationFunctionPair
import org.yuqi.nn.math.IErrorFunctionPair
import org.yuqi.util.compress
import org.yuqi.util.decompress
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class NeuralNetwork(
    val inputSize: Int,
    val outputSize: Int,
    hiddenLayerSizes: Iterable<Int>,
    activationFunctions: Iterable<IActivationFunctionPair>? = null,
) {
    companion object {
        fun load(file: File, compressed: Boolean = true): NeuralNetwork {
            val bytes = file.readBytes().let { if (compressed) it.decompress() else it }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val numLayers = buffer.getInt()
            val inputSize = buffer.getInt()
            val layerSizes = IntArray(numLayers) { buffer.getInt() }
            val learningRate = buffer.getFloat()
            val momentum = buffer.getFloat()
            val activationFunctions = mutableListOf<IActivationFunctionPair>()
            for (i in 0 until numLayers) {
                val name = ByteArray(buffer.getInt())
                buffer.get(name)
                activationFunctions.add(ActivationFunctions.valueOf(String(name)))
            }
            val nn = NeuralNetwork(inputSize, layerSizes.last(), layerSizes.dropLast(1), activationFunctions).apply {
                this.learningRate = learningRate
                this.momentum = momentum
            }
            for (layer in nn.layers) {
                for (weights in layer.weights)
                    buffer.asFloatBuffer().get(weights)
                buffer.asFloatBuffer().get(layer.biases)
            }
            return nn
        }
    }

    private val layers: List<Layer>
    var errorFunction: IErrorFunctionPair = ErrorFunctions.MSE
    var learningRate: Float = 0.1F
    var momentum: Float = 0F

    init {
        val hls = hiddenLayerSizes.toList()
        val af = activationFunctions?.toList() ?: List(hls.size + 1) { ActivationFunctions.LINEAR }
        assert(af.size == hls.size + 1) { "Incompatible sizes" }
        layers = hls.plus(outputSize).mapIndexed { idx, size -> Layer(if (idx == 0) inputSize else hls[idx - 1], size, af[idx]) }
    }

    constructor(
        inputSize: Int,
        outputSize: Int,
        hiddenLayerSizes: Collection<Int>,
        activationFunction: IActivationFunctionPair
    ) : this(
        inputSize,
        outputSize,
        hiddenLayerSizes,
        List(1 + hiddenLayerSizes.size) { activationFunction }
    )

    fun reset() = layers.forEach { it.reset() }
    fun reset(range: OpenEndRange<Float>, random: Random? = null) = layers.forEach { it.reset(range, random) }

    fun save(file: File, compressed: Boolean = true) {
        assert(layers.all { it.activate is ActivationFunctions }) { "Only built-in activation functions are supported" }
        val funNames = layers.map { (it.activate as ActivationFunctions).name }
        val numBytes = (2 + layers.size) * Int.SIZE_BYTES +
            2 * Float.SIZE_BYTES +
            funNames.sumOf { Int.SIZE_BYTES + it.length } +
            layers.sumOf { it.weights.size * it.weights[0].size + it.biases.size } * Float.SIZE_BYTES
        val buffer = ByteBuffer.allocate(numBytes).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(layers.size).putInt(inputSize)
        for (layer in layers)
            buffer.putInt(layer.outputSize)
        buffer.putFloat(learningRate).putFloat(momentum)
        for (name in funNames) {
            buffer.putInt(name.length)
            buffer.put(name.toByteArray())
        }
        for (layer in layers) {
            for (weights in layer.weights)
                buffer.asFloatBuffer().put(weights)
            buffer.asFloatBuffer().put(layer.biases)
        }
        file.writeBytes(buffer.array().let { if (compressed) it.compress() else it })
    }

    fun predict(inputs: FloatArray) = layers.fold(inputs) { acc, layer -> layer.feedForward(acc) }

    private fun backPropagate(inputs: FloatArray, expected: FloatArray) {
        val outputs = layers.last().outputs.asIterable()
        val dActivate = layers.last().activate.dF
        var deltas = errorFunction.dF(outputs, expected.asIterable()).zip(outputs) { e, o -> e * dActivate(o) }

        for ((j, layer) in layers.withIndex().reversed()) {
            val newDeltas = MutableList(layer.inputSize) { 0F }
            for (k in 0 until layer.outputSize) {
                for (l in 0 until layer.inputSize) {
                    val input = if ((j == 0)) inputs[l] else layers[j - 1].outputs[l]
                    val update = momentum * layer.weightUpdates[k][l] + learningRate * deltas[k] * input
                    layer.weights[k][l] -= update
                    layer.weightUpdates[k][l] = update
                }
                val update = momentum * layer.biasUpdates[k] + learningRate * deltas[k]
                layer.biases[k] -= update
                layer.biasUpdates[k] = update
                if (j > 0) {
                    for (l in 0 until layer.inputSize)
                        newDeltas[l] += deltas[k] * layer.weights[k][l] * layer.activate.dF(layers[j - 1].outputs[l])
                }
            }
            deltas = newDeltas
        }
    }

    fun train(inputs: FloatArray, expected: FloatArray): FloatArray {
        val outputs = predict(inputs)
        backPropagate(inputs, expected)
        return outputs
    }

    fun train(data: Iterable<Pair<FloatArray, FloatArray>>, epochs: Int) = repeat(epochs) {
        for ((inputs, expected) in data)
            train(inputs, expected)
    }
}