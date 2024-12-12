package org.yuqi.robot.bridge

import org.yuqi.util.ArrayTable
import org.yuqi.util.IQTable
import java.io.File

class LocalQTable(file: File, compressed: Boolean = true) : IQTable<State, Action> {
    private val table = ArrayTable(State.maxIndex, Action.maxIndex, file, compressed)
    val file: File get() = table.file

    override val actions: Iterable<Action> = Action.entries
    override val actionSpace: Int get() = Action.maxIndex

    override fun get(state: State, action: Action): Float = table[state.index, action.index]
    override fun set(state: State, action: Action, value: Float) {
        table[state.index, action.index] = value
    }

    override fun getRow(state: State): List<Pair<Action, Float>> = actions.zip(table.getRow(state.index))

    fun load() = table.load()
    fun save() = table.save()
}