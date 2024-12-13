@file:Suppress("ConstPropertyName", "unused")

package org.yuqi.robot

import org.yuqi.nn.math.ActivationFunctions
import org.yuqi.rl.Environment
import org.yuqi.rl.algorithm.QLearning
import org.yuqi.rl.algorithm.SARSA
import org.yuqi.rl.algorithm.lambdaGreedy
import org.yuqi.robot.bridge.*
import org.yuqi.util.IPersistentQTable
import java.io.File
import java.nio.file.Paths
import java.time.Instant
import java.time.format.DateTimeFormatter

object Params {
    const val freshStart = false
    val rlAlgorithm = QLearning::class.simpleName!!
    val rlTable = DqnTrainingInterface::class.simpleName!!
    const val rlLearningRate = 0.2
    const val rlDiscountFactor = 0.9
    const val rlExplorationRate = 0.1F
    const val nnLearningRate = 0.1F
    const val nnMomentum = 0F
    val nnHiddenLayers: List<Pair<Int, ActivationFunctions>> = listOf(
        20 to ActivationFunctions.SIGMOID,
        10 to ActivationFunctions.SIGMOID
    )
}

object Files {
    val qTable: File = Paths.get("robots/qtable.bin").toFile()
    val dqn: File = Paths.get("robots/dqn.bin").toFile()
    val record = lazy {
        val time = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(Instant.now())
        Paths.get("../RobocodeRL/plot/${Params.rlAlgorithm}-${Params.rlTable}-${time}.txt").toFile()
    }
}

private val qTable = lazy {
    if (Params.freshStart && Files.qTable.exists())
        Files.qTable.delete()
    LocalQTable(Files.qTable).apply {
        if (Files.qTable.exists())
            load()
    }
}

private val dqn = lazy {
    if (Params.freshStart && Files.dqn.exists())
        Files.dqn.delete()
    if (Files.dqn.exists())
        DqnInterface(Files.dqn)
    else {
        DqnInterface(Params.nnHiddenLayers, Files.dqn).apply {
            nn.learningRate = Params.nnLearningRate
            nn.momentum = Params.nnMomentum
            nn.reset(0F..<1F)
        }
    }
}

private val dqnTraining = lazy {
    assert(!Params.freshStart && Files.qTable.exists()) { "QTable file does not exist" }
    val qTable = LocalQTable(Files.qTable).apply { load() }
    if (Files.dqn.exists())
        DqnTrainingInterface(Files.dqn, qTable)
    else {
        DqnTrainingInterface(Params.nnHiddenLayers, Files.dqn, qTable).apply {
            nn.learningRate = Params.nnLearningRate
            nn.momentum = Params.nnMomentum
            nn.reset(0F..<1F)
        }
    }
}

val env = Environment<State, Action>()
val table: IPersistentQTable<State, Action> = when (Params.rlTable) {
    LocalQTable::class.simpleName          -> qTable.value
    DqnInterface::class.simpleName         -> dqn.value
    DqnTrainingInterface::class.simpleName -> dqnTraining.value
    else                                   -> throw IllegalArgumentException("Unknown table type")
}
val rl = when (Params.rlAlgorithm) {
    QLearning::class.simpleName -> QLearning(env, table, Params.rlLearningRate, Params.rlDiscountFactor, lambdaGreedy(Params.rlExplorationRate))
    SARSA::class.simpleName     -> SARSA(env, table, Params.rlLearningRate, Params.rlDiscountFactor, lambdaGreedy(Params.rlExplorationRate))
    else                        -> throw IllegalArgumentException("Unknown RL algorithm")
}