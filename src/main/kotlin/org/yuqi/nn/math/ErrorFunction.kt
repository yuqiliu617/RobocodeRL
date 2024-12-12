@file:Suppress("NOTHING_TO_INLINE")

package org.yuqi.nn.math

typealias ErrorFunction = (outputs: Iterable<Float>, expected: Iterable<Float>) -> Float

typealias ErrorFunctionDerivative = (outputs: Iterable<Float>, expected: Iterable<Float>) -> List<Float>

private inline fun sqr(x: Float): Float = x * x

val mse: ErrorFunction = { outputs, expected ->
    outputs.zip(expected).run {
        fold(0F) { acc, (output, e) -> acc + sqr(output - e) } / size
    }
}

val dMse: ErrorFunctionDerivative = { outputs, expected ->
    outputs.zip(expected).run {
        val coe = 2F / size
        map { (output, e) -> (output - e) * coe }
    }
}

interface IErrorFunctionPair {
    val f: ErrorFunction
    val dF: ErrorFunctionDerivative
}

enum class ErrorFunctions(override val f: ErrorFunction, override val dF: ErrorFunctionDerivative) : IErrorFunctionPair {
    MSE(mse, dMse)
}