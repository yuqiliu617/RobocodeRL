package org.yuqi.util

interface IQTable<TState, TAction> {
    val actions: Iterable<TAction>
    val actionSpace: Int
        get() = (actions as? Collection<TAction>)?.size ?: actions.count()

    operator fun get(state: TState, action: TAction): Float
    operator fun set(state: TState, action: TAction, value: Float)

    fun getRow(state: TState): List<Pair<TAction, Float>>
        = actions.map { it to get(state, it) }
}

