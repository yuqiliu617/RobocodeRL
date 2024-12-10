package org.yuqi.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Point(var x: Double, var y: Double) {
    val length: Double
        get() = sqrt(x * x + y * y)
    val radian: Double
        get() = asin(y / length).let { if (x < 0) Math.PI - it else it }

    operator fun unaryMinus(): Point = Point(-x, -y)
    operator fun plus(other: Point): Point = Point(x + other.x, y + other.y)
    operator fun minus(other: Point): Point = Point(x - other.x, y - other.y)
    operator fun times(k: Double): Point = Point(x * k, y * k)
    operator fun div(k: Double): Point = Point(x / k, y / k)

    fun toPolarPoint(): PolarPoint = PolarPoint(length, radian)
}

class PolarPoint(var length: Double, radian: Double) {
    var radian: Double = radian
        set(value) {
            field = normalizeRadian(value)
        }

    val x: Double
        get() = length * cos(radian)
    val y: Double
        get() = length * sin(radian)

    operator fun times(k: Double): PolarPoint = PolarPoint(length * k, radian)
    operator fun div(k: Double): PolarPoint = PolarPoint(length / k, radian)

    fun clockwiseRotate(radian: Double): PolarPoint = PolarPoint(length, this.radian - radian)
    fun toPoint(): Point = Point(x, y)
}

const val modulus = Math.PI * 2
fun normalizeRadian(radian: Double, positive: Boolean = false): Double {
    if (!positive) {
        val rad = if (radian <= -modulus || radian >= modulus) radian % modulus else radian
        return when {
            rad <= -Math.PI -> rad + modulus
            rad > Math.PI  -> rad - modulus
            else           -> rad
        }
    } else {
        if (radian >= 0 && radian < modulus)
            return radian
        val rad = radian % modulus
        return if (rad < modulus) rad + modulus else rad
    }
}