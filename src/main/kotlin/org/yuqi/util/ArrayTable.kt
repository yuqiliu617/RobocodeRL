package org.yuqi.util

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

open class ArrayTable(val xSize: Int, val ySize: Int, val file: File, val compressed: Boolean) {
    private var _data: FloatArray? = null

    private val data: FloatArray
        get() {
            if (_data == null)
                _data = FloatArray(xSize * ySize) { 0.0F }
            return _data!!
        }

    val array: Array<Float>
        get() = data.toTypedArray()

    operator fun get(x: Int, y: Int): Float = data[x * ySize + y]

    operator fun set(x: Int, y: Int, value: Float) {
        data[x * ySize + y] = value
    }

    fun init() {
        if (_data == null)
            _data = FloatArray(xSize * ySize) { 0.0F }
        else
            _data!!.fill(0.0F)
    }

    fun getRow(x: Int): List<Float> = data.slice(x * ySize..<(x + 1) * ySize)

    fun getColumn(y: Int): List<Float> = data.slice(y..<xSize * ySize step ySize)

    fun load() {
        val bytes = file.readBytes().let { if (compressed) it.decompress() else it }
        assert(bytes.size == xSize * ySize * Float.SIZE_BYTES)
        _data = FloatArray(xSize * ySize)
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(_data)
    }

    fun save() {
        val buffer = ByteBuffer.allocate(_data!!.size * Float.SIZE_BYTES)
        buffer.asFloatBuffer().put(_data!!)
        file.writeBytes(buffer.array().let { if (compressed) it.compress() else it })
    }
}