package org.yuqi.util

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

open class ArrayTable(val xSize: Int, val ySize: Int) {
    protected val data: FloatArray = FloatArray(xSize * ySize)

    val array: Array<Float> get() = data.toTypedArray()

    operator fun get(x: Int, y: Int): Float = data[x * ySize + y]
    operator fun set(x: Int, y: Int, value: Float) {
        data[x * ySize + y] = value
    }

    operator fun get(x: Int): List<Float> = getRow(x)

    fun init() {
        data.fill(0F)
    }

    fun getRow(x: Int): List<Float> = data.slice(x * ySize..<(x + 1) * ySize)

    fun getColumn(y: Int): List<Float> = data.slice(y..<xSize * ySize step ySize)
}

class PersistentArrayTable(xSize: Int, ySize: Int, val file: File, val compressed: Boolean) : ArrayTable(xSize, ySize) {
    fun load() {
        val bytes = file.readBytes().let { if (compressed) it.decompress() else it }
        assert(bytes.size == xSize * ySize * Float.SIZE_BYTES)
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(data)
    }

    fun save() {
        val buffer = ByteBuffer.allocate(data.size * Float.SIZE_BYTES)
        buffer.asFloatBuffer().put(data)
        file.writeBytes(buffer.array().let { if (compressed) it.compress() else it })
    }
}