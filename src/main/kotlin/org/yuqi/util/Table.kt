package org.yuqi.util

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ArrayTable(override val xSize: Int, override val ySize: Int, val file: File, val compressed: Boolean) : IPersistentTable<Float> {
    private var _data: FloatArray? = null

    private val data: FloatArray
        get() {
            if (_data == null)
                _data = FloatArray(xSize * ySize) { 0.0F }
            return _data!!
        }

    override val array: Array<Float>
        get() = data.toTypedArray()

    override fun get(x: Int, y: Int): Float = data[x * ySize + y]

    override fun set(x: Int, y: Int, value: Float) {
        data[x * ySize + y] = value
    }

    override fun init() {
        if (_data == null)
            _data = FloatArray(xSize * ySize) { 0.0F }
        else
            _data!!.fill(0.0F)
    }

    override fun getRow(x: Int): List<Float> = data.slice(x * ySize..<(x + 1) * ySize)

    override fun getColumn(y: Int): List<Float> = data.slice(y..<xSize * ySize step ySize)

    override fun load() {
        val bytes = file.readBytes().let { if (compressed) it.decompress() else it }
        assert(bytes.size == xSize * ySize * Float.SIZE_BYTES)
        _data = FloatArray(xSize * ySize)
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(_data)
        println("Exploration Rate At Start: ${(_data!!.count { it != 0F }.toDouble() / _data!!.size * 100).format()}%")
    }

    override fun save() {
        println("Exploration Rate At End: ${(_data!!.count { it != 0F }.toDouble() / _data!!.size * 100).format()}%")
        val buffer = ByteBuffer.allocate(_data!!.size * Float.SIZE_BYTES)
        buffer.asFloatBuffer().put(_data!!)
        file.writeBytes(buffer.array().let { if (compressed) it.compress() else it })
    }
}

interface ITable<T> {
    val xSize: Int
    val ySize: Int
    val array: Array<T>
    operator fun get(x: Int, y: Int): T
    operator fun set(x: Int, y: Int, value: T)
    operator fun get(x: Int) = getRow(x)
    fun init()
    fun getRow(x: Int): List<T>
    fun getColumn(y: Int): List<T>
}

interface IPersistentTable<T> : ITable<T> {
    fun load()
    fun save()
}