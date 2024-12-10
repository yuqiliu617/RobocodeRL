package org.yuqi.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

fun Any.discard() = Unit
fun Double.format(digits: Int = 2) = "%.${digits}f".format(this)
fun Float.format(digits: Int = 2) = "%.${digits}f".format(this)
fun Boolean.toInt() = if (this) 1 else 0
fun Boolean.toDouble() = if (this) 1.0 else 0

fun ByteArray.toInt(start: Int = 0): Int {
    assert(start + 4 < this.size)
    var result: Int = 0
    for (i in start..(start + 3))
        result = (result shl 8) or this[i].toInt()
    return result
}

fun ByteArray.compress(): ByteArray =
    ByteArrayOutputStream(size).use { stream ->
        GZIPOutputStream(stream).use { gzip -> gzip.write(this) }
        return stream.toByteArray()
    }

fun ByteArray.decompress(): ByteArray =
    ByteArrayInputStream(this).use { stream ->
        GZIPInputStream(stream).use { gzip -> gzip.readBytes() }
    }

fun List<Int>.product(): Int = fold(1) { r, v -> r * v }

fun encodeIndex(values: List<Int>, maxValues: List<Int>): Int {
    assert(values.size == maxValues.size)
    return values.zip(maxValues).fold(0) { r, pair -> r * pair.second + pair.first }
}

fun decodeIndex(index: Int, maxValues: List<Int>): List<Int> {
    assert(index >= 0 && index < maxValues.product())
    return sequence {
        var value = index
        for (max in maxValues.asReversed()) {
            yield(value % max)
            value /= max
        }
    }.toList().asReversed()
}