package org.yuqi.rl

class Environment<TState : IState, TAction : IIndexable> {
    var curState: TState? = null
        private set
    var finished: Boolean = true
        private set

    val startEventListeners: MutableCollection<(TState) -> Unit> = mutableListOf()
    val interactEventListeners: MutableCollection<(TAction) -> Unit> = mutableListOf()
    val updateEventListeners: MutableCollection<(TState, TState) -> Unit> = mutableListOf()
    val finishEventListeners: MutableCollection<(TState) -> Unit> = mutableListOf()

    fun onStart(state: TState) {
        finished = false
        curState = state
        startEventListeners.forEach { it(state) }
    }

    fun onInteract(action: TAction) {
        interactEventListeners.forEach { it(action) }
    }

    fun onUpdate(state: TState) {
        val oldState = curState!!
        curState = state
        updateEventListeners.forEach { it(state, oldState) }
    }

    fun onFinish(state: TState) {
        finished = true
        finishEventListeners.forEach { it(state) }
    }
}

interface IIndexableStatic {
    val maxIndex: Int
}

interface IIndexable {
    val index: Int
}

interface IState : IIndexable {
    val reward: Float
}