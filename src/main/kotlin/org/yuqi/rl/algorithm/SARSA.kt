package org.yuqi.rl.algorithm

import org.yuqi.rl.Environment
import org.yuqi.rl.IIndexable
import org.yuqi.rl.IState
import org.yuqi.util.IQTable

class SARSA<TState : IState, TAction : IIndexable>(
    env: Environment<TState, TAction>,
    table: IQTable<TState, TAction>,
    lr: Double,
    decay: Double,
    policy: Policy<Float>
) : LUT<TState, TAction>(env, table, lr, decay, policy) {
    private var prevAction: TAction? = null
    private var curAction: TAction? = null

    private fun chooseAction(state: TState): TAction {
        val row = table.getRow(state)
        val choice = policy(row.map { it.second }).index
        return row[choice].first
    }

    private fun updateTable(cur: TState, prev: TState) {
        val reward = cur.reward
        roundReward += reward
        val oldValue = table[prev, prevAction!!]
        table[prev, prevAction!!] = oldValue + (learningRate * (reward + decayFactor * table[cur, curAction!!] - oldValue)).toFloat()
    }

    override fun onStart(state: TState) {
        super.onStart(state)
        prevAction = chooseAction(state)
        env.onInteract(prevAction!!)
    }

    override fun onUpdate(cur: TState, prev: TState) {
        super.onUpdate(cur, prev)
        curAction = chooseAction(cur)
        updateTable(cur, prev)
        prevAction = curAction
        env.onInteract(prevAction!!)
    }

    override fun onFinish(state: TState) {
        curAction = chooseAction(state)
        updateTable(state, env.curState!!)
        super.onFinish(state)
    }
}