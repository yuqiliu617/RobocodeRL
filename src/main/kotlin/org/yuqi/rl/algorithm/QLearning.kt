package org.yuqi.rl.algorithm

import org.yuqi.rl.Environment
import org.yuqi.rl.IIndexable
import org.yuqi.rl.IState
import org.yuqi.util.ITable

class QLearning<TState : IState, TAction : IIndexable>(
    env: Environment<TState, TAction>,
    table: ITable<Float>,
    lr: Double,
    decay: Double,
    indexToAction: (index: Int) -> TAction,
    policy: Policy<Float>
) : LUT<TState, TAction>(env, table, lr, decay, indexToAction, policy) {
    private var lastActionIndex: Int = -1

    constructor(
        env: Environment<TState, TAction>,
        table: ITable<Float>,
        lr: Double,
        decay: Double,
        indexToAction: (index: Int) -> TAction,
        lambda: Float = 1 - table.array.let { it.count { v -> v != 0F } / it.size.toFloat() }
    ) : this(env, table, lr, decay, indexToAction, lambdaGreedy(lambda) )

    private fun chooseAction(state: TState): Int {
        lastActionIndex = policy(table[state.index])
        return lastActionIndex
    }

    private fun updateTable(cur: TState, prev: TState): Float {
        val reward = cur.reward
        roundReward += reward
        table[prev.index, lastActionIndex] += (learningRate * (reward + decayFactor * table[cur.index].max() - table[prev.index, lastActionIndex])).toFloat()
        return table[prev.index, lastActionIndex]
    }

    override fun onStart(state: TState) {
        super.onStart(state)
        val actionIdx = chooseAction(state)
        env.onInteract(indexToAction(actionIdx))
    }

    override fun onUpdate(cur: TState, prev: TState) {
        super.onUpdate(cur, prev)
        updateTable(cur, prev)
        val actionIdx = chooseAction(cur)
        env.onInteract(indexToAction(actionIdx))
    }

    override fun onFinish(state: TState) {
        updateTable(state, env.curState!!)
        super.onFinish(state)
    }
}