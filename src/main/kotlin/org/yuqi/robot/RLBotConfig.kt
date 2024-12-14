package org.yuqi.robot

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.yuqi.nn.math.ActivationFunctions
import org.yuqi.rl.Environment
import org.yuqi.rl.algorithm.QLearning
import org.yuqi.rl.algorithm.SARSA
import org.yuqi.rl.algorithm.lambdaGreedy
import org.yuqi.robot.bridge.*
import org.yuqi.util.IPersistentQTable
import java.io.File
import java.nio.file.Paths

@Serializable
data class RLBotConfig(
    var rlAlgorithm: String,
    var rlTable: String,
    var rlLearningRate: Double,
    var rlDiscountFactor: Double,
    var rlExplorationRate: Float,
    var nnTraining: Boolean,
    var nnLearningRate: Float,
    var nnMomentum: Float,
    var nnHiddenLayerNeuronCount: Int,
    var nnHiddenLayerActivationFunction: String,
    var dqnBatchUpdate: Int = 1,
    var qTableFileName: String? = null,
    var dqnFileName: String? = null,
    var recordFileName: String? = null
) {
    companion object {
        @JvmStatic
        val defaultFile: File = Paths.get("robots/RLBot.json").toFile()

        @JvmStatic
        fun load(file: File): RLBotConfig {
            val jsonText = file.readText()
            return Json.decodeFromString(serializer(), jsonText)
        }
    }

    val qTableFile: File? get() = qTableFileName?.let { Paths.get(it).toFile() }
    val dqnFile: File? get() = dqnFileName?.let { Paths.get(it).toFile() }
    val recordFile: File? get() = recordFileName?.let { Paths.get(it).toFile() }

    fun save(file: File) {
        val jsonText = Json.encodeToString(serializer(), this)
        file.writeText(jsonText)
    }
}

val config = RLBotConfig.load(RLBotConfig.defaultFile)

private val qTable = lazy {
    requireNotNull(config.qTableFileName)
    LocalQTable(config.qTableFile!!).apply {
        if (file.exists())
            load()
    }
}

private val dqn = lazy {
    requireNotNull(config.dqnFileName)
    if (config.dqnFile!!.exists())
        DqnInterface(config.dqnFile!!)
    else {
        val activateFunc = ActivationFunctions.valueOf(config.nnHiddenLayerActivationFunction)
        DqnInterface(listOf(config.nnHiddenLayerNeuronCount to activateFunc), config.dqnFile!!, config.dqnBatchUpdate).apply {
            nn.learningRate = config.nnLearningRate
            nn.momentum = config.nnMomentum
            training = config.nnTraining
            nn.reset(0F..<1F)
        }
    }
}

private val dqnTraining = lazy {
    require(config.qTableFile?.exists() == true) { "QTable file does not exist" }
    requireNotNull(config.dqnFileName)
    val qTable = LocalQTable(config.qTableFile!!).apply { load() }
    val dqnFile = config.dqnFile!!
    if (dqnFile.exists())
        DqnTrainingInterface(dqnFile, qTable)
    else {
        val activateFunc = ActivationFunctions.valueOf(config.nnHiddenLayerActivationFunction)
        DqnTrainingInterface(listOf(config.nnHiddenLayerNeuronCount to activateFunc), dqnFile, qTable).apply {
            nn.learningRate = config.nnLearningRate
            nn.momentum = config.nnMomentum
            training = config.nnTraining
            nn.reset(0F..<1F)
        }
    }
}

val env = Environment<State, Action>()
val table: IPersistentQTable<State, Action> = when (config.rlTable) {
    LocalQTable::class.simpleName          -> qTable.value
    DqnInterface::class.simpleName         -> dqn.value
    DqnTrainingInterface::class.simpleName -> dqnTraining.value
    else                                   -> throw IllegalArgumentException("Unknown table type: ${config.rlTable}")
}
val rl = when (config.rlAlgorithm) {
    QLearning::class.simpleName -> QLearning(env, table, config.rlLearningRate, config.rlDiscountFactor, lambdaGreedy(config.rlExplorationRate))
    SARSA::class.simpleName     -> SARSA(env, table, config.rlLearningRate, config.rlDiscountFactor, lambdaGreedy(config.rlExplorationRate))
    else                        -> throw IllegalArgumentException("Unknown RL algorithm: ${config.rlAlgorithm}")
}