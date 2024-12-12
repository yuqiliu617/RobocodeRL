package org.yuqi.rl.algorithm

import org.yuqi.rl.Environment
import org.yuqi.rl.IIndexable
import org.yuqi.rl.IState
import org.yuqi.util.IQTable

class QLearning<TState : IState, TAction : IIndexable>(
    env: Environment<TState, TAction>,
    table: IQTable<TState, TAction>,
    lr: Double,
    decay: Double,
    policy: Policy<Float>
) : LUT<TState, TAction>(env, table, lr, decay, policy) {
    private var lastAction: TAction? = null

    private fun chooseAction(state: TState): TAction {
        val row = table.getRow(state)
        val idx = policy(row.map { it.second }).index
        lastAction = IndexedValue(idx, row[idx].first).value
        return lastAction!!
    }

    private fun updateTable(cur: TState, prev: TState) {
        val reward = cur.reward
        roundReward += reward
        val oldValue = table[prev, lastAction!!]
        val bestAction = table.getRow(cur).maxBy { it.second }
        table[prev, lastAction!!] = oldValue + (learningRate * (reward + decayFactor * bestAction.second - oldValue)).toFloat()
    }

    override fun onStart(state: TState) {
        super.onStart(state)
        val action = chooseAction(state)
        env.onInteract(action)
    }

    override fun onUpdate(cur: TState, prev: TState) {
        super.onUpdate(cur, prev)
        updateTable(cur, prev)
        val action = chooseAction(cur)
        env.onInteract(action)
    }

    override fun onFinish(state: TState) {
        updateTable(state, env.curState!!)
        super.onFinish(state)
    }
}