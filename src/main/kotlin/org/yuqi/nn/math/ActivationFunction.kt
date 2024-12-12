package org.yuqi.nn.math

import kotlin.math.exp
import kotlin.math.max

typealias ActivationFunction = (x: Float) -> Float

val sigmoid: ActivationFunction = { x -> 1.0F / (1.0F + exp(-x)) }

val dSigmoid: ActivationFunction = { y -> y * (1 - y) }

val tanh: ActivationFunction = { x -> exp(x).let { (it - 1.0F / it) / (it + 1.0F / it) } }

val dTanh: ActivationFunction = { y -> 1 - y * y }

val relu: ActivationFunction = { x -> max(0F, x) }

val dRelu: ActivationFunction = { x -> if (x > 0) 1F else 0F }

val swish: ActivationFunction = { x -> x * sigmoid(x) }

val dSwish: ActivationFunction = { x -> sigmoid(x).let { it + x * it * (1 - it) } }

fun customizeLeakyRelu(alpha: Float): ActivationFunction = { x -> if (x > 0) x else alpha * x }

fun customizeDLeakyRelu(alpha: Float): ActivationFunction = { x -> if (x > 0) 1F else alpha }

val leakyRelu = customizeLeakyRelu(0.01F)

val dLeakyRelu = customizeDLeakyRelu(0.01F)

fun customizeElu(alpha: Float): ActivationFunction = { x -> if (x > 0) x else alpha * (exp(x) - 1) }

fun customizeDElu(alpha: Float): ActivationFunction = { x -> if (x > 0) 1F else alpha * exp(x) }

val elu = customizeElu(1F)

val dElu = customizeDElu(1F)

interface IActivationFunctionPair {
    val f: ActivationFunction
    val dF: ActivationFunction
}

open class ActivationFunctionPair(override val f: ActivationFunction, override val dF: ActivationFunction) : IActivationFunctionPair {
    companion object {
        fun customizeLeakyReluPair(alpha: Float): ActivationFunctionPair {
            val f = customizeLeakyRelu(alpha)
            val dF = customizeDLeakyRelu(alpha)
            return ActivationFunctionPair(f, dF)
        }

        fun customizeEluPair(alpha: Float): ActivationFunctionPair {
            val f = customizeElu(alpha)
            val dF = customizeDElu(alpha)
            return ActivationFunctionPair(f, dF)
        }
    }
}

enum class ActivationFunctions(override val f: ActivationFunction, override val dF: ActivationFunction) : IActivationFunctionPair {
    LINEAR({ x -> x }, { 1F }),
    SIGMOID(sigmoid, dSigmoid),
    TANH(tanh, dTanh),
    RELU(relu, dRelu),
    SWISH(swish, dSwish),
    LEAKY_RELU(leakyRelu, dLeakyRelu),
    ELU(elu, dElu);
}