package org.yuqi.rl.algorithm

import org.yuqi.rl.Environment
import org.yuqi.rl.IIndexable
import org.yuqi.rl.IState
import org.yuqi.util.IQTable

abstract class LUT<TState : IState, TAction : IIndexable>(
    val env: Environment<TState, TAction>,
    val table: IQTable<TState, TAction>,
    var learningRate: Double,
    var decayFactor: Double,
    val policy: Policy<Float>
) {
    protected var epoch: Int = -1

    var roundReward: Double = 0.0
        protected set
    var totalReward: Double = 0.0
        private set
    var started: Boolean = false
        private set

    protected open fun onStart(state: TState) {
        ++epoch
        roundReward = 0.0
    }

    protected open fun onUpdate(cur: TState, prev: TState) {}
    protected open fun onFinish(state: TState) {
        totalReward += roundReward
    }

    fun start() {
        if (started)
            return
        started = true
        env.startEventListeners.add(this::onStart)
        env.updateEventListeners.add(this::onUpdate)
        env.finishEventListeners.add(this::onFinish)
    }

    fun stop() {
        if (!started)
            return
        started = false
        env.startEventListeners.remove(this::onStart)
        env.updateEventListeners.remove(this::onUpdate)
        env.finishEventListeners.remove(this::onFinish)
    }
}