package org.yuqi.rl.algorithm

import org.yuqi.rl.Environment
import org.yuqi.rl.IIndexable
import org.yuqi.rl.IState
import org.yuqi.util.ITable

class SARSA<TState : IState, TAction : IIndexable>(
    env: Environment<TState, TAction>,
    table: ITable<Float>,
    lr: Double,
    decay: Double,
    indexToAction: (index: Int) -> TAction,
    policy: Policy<Float>
) : LUT<TState, TAction>(env, table, lr, decay, indexToAction, policy) {
    private var prevActionIdx: Int = -1
    private var curActionIdx: Int = -1

    constructor(
        env: Environment<TState, TAction>,
        table: ITable<Float>,
        lr: Double,
        decay: Double,
        indexToAction: (index: Int) -> TAction,
        lambda: Float = 1 - table.array.let { it.count { v -> v != 0F } / it.size.toFloat() }
    ) : this(env, table, lr, decay, indexToAction, lambdaGreedy(lambda))

    private fun chooseAction(state: TState): Int = policy(table[state.index])

    private fun updateTable(cur: TState, prev: TState): Float {
        val reward = cur.reward
        roundReward += reward
        table[prev.index, prevActionIdx] += (learningRate * (reward + decayFactor * table[cur.index, curActionIdx] - table[prev.index, prevActionIdx])).toFloat()
        return table[prev.index, prevActionIdx]
    }

    override fun onStart(state: TState) {
        super.onStart(state)
        prevActionIdx = chooseAction(state)
        env.onInteract(indexToAction(prevActionIdx))
    }

    override fun onUpdate(cur: TState, prev: TState) {
        super.onUpdate(cur, prev)
        curActionIdx = chooseAction(cur)
        updateTable(cur, prev)
        prevActionIdx = curActionIdx
        env.onInteract(indexToAction(prevActionIdx))
    }

    override fun onFinish(state: TState) {
        curActionIdx = chooseAction(state)
        updateTable(state, env.curState!!)
        super.onFinish(state)
    }
}