@file:Suppress("DuplicatedCode")

package org.yuqi

import org.yuqi.robot.RLBot
import org.yuqi.robot.RLBotConfig
import org.yuqi.robot.bridge.DqnInterface
import org.yuqi.robot.bridge.DqnTrainingInterface
import org.yuqi.util.format
import org.yuqi.util.putArray
import org.yuqi.util.toInt
import robocode.Robocode
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun runRobocode(
    robots: Iterable<String>,
    roundCount: Int = 10,
    battlefieldWidth: Int = 800,
    battlefieldHeight: Int = 800,
    showWindow: Boolean = false
) {
    System.setProperty("NOSECURITY", "true")
    val file = Paths.get("battles/test.battle").toFile()
    with(StringBuilder()) {
        appendLine("robocode.battle.numRounds=$roundCount")
        appendLine("robocode.battleField.width=$battlefieldWidth")
        appendLine("robocode.battleField.height=$battlefieldHeight")
        appendLine("robocode.battle.selectedRobots=${robots.joinToString(", ")}")
        appendLine(
            """
            robocode.battle.sentryBorderSize=100
            robocode.battle.gunCoolingRate=0.1
            robocode.battle.rules.inactivityTime=450
            robocode.battle.hideEnemyNames=false
        """.trimIndent()
        )
        file.writeText(toString())
    }
    val options = mutableListOf("-battle", file.absolutePath, "-tps", "100000")
    options.add(if (showWindow) "-minimize" else "-nodisplay")
    Robocode.main(options.toTypedArray())
}

val currentTimeString: String
    get() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))

val plotRoot = Paths.get("../RobocodeRL/plot").toFile()

fun parseRecordFile(file: File): BooleanArray {
    assert(file.exists())
    val data = file.readBytes()
    val result = BooleanArray(data.size)
    val zero = 0.toByte()
    for (i in data.indices)
        result[i] = data[i] != zero
    return result
}

fun toWinRate(record: BooleanArray): FloatArray {
    val winCounts = IntArray(record.size)
    winCounts[0] = 0
    for (i in 1 until record.size)
        winCounts[i] = winCounts[i - 1] + record[i].toInt()
    return winCounts.mapIndexed { i, count -> count.toFloat() / (i + 1) }.toFloatArray()
}

private fun waitForFile(file: File, checkInterval: Duration = Duration.ofSeconds(1)) {
    while (!file.exists())
        Thread.sleep(checkInterval.toMillis())
}

@OptIn(ExperimentalUuidApi::class)
fun evaluateRLBot(config: RLBotConfig, opponent: String, rounds: Int, showWindow: Boolean = false) {
    val noRecordFile = config.recordFile == null
    if (noRecordFile)
        config.recordFileName = "robots/${Uuid.random()}.bin"
    config.save(RLBotConfig.defaultFile)
    runRobocode(listOf(opponent, RLBot::class.java.name), rounds, showWindow = showWindow)
    waitForFile(config.recordFile!!)
    if (noRecordFile) {
        config.recordFile!!.delete()
        config.recordFileName = null
    }
}

fun testQTableTraining(
    opponent: String,
    trainingRounds: Int,
    testRounds: Int,
    batch: Int = 1,
    learningRate: Double = 5e-5,
    momentum: Double = 0.15,
    hiddenLayerNeuronCount: Int = 60
): IntArray {
    val configBackup = RLBotConfig.defaultFile.readBytes()
    val config = RLBotConfig.load(RLBotConfig.defaultFile).apply {
        rlAlgorithm = "QLearning"
        nnLearningRate = learningRate.toFloat()
        nnMomentum = momentum.toFloat()
        nnHiddenLayerNeuronCount = hiddenLayerNeuronCount
        nnHiddenLayerActivationFunction = "Sigmoid"
        qTableFileName = "robots/QTable.bin"
        dqnFileName = "robots/DQN.bin"
    }
    val results = IntArray(batch)
    with(config) {
        repeat(batch) { idx ->
            // Train
            rlTable = DqnTrainingInterface::class.simpleName!!
            nnTraining = true
            dqnFile!!.delete()
            evaluateRLBot(config, opponent, trainingRounds)
            System.err.println("[$idx] Training finished")
            // Test
            rlTable = DqnInterface::class.simpleName!!
            nnTraining = false
            recordFileName = "robots/TestRecord-$currentTimeString.bin"
            evaluateRLBot(config, opponent, testRounds)
            val record = parseRecordFile(recordFile!!)
            results[idx] = record.sumOf { it.toInt() }
            recordFile!!.delete()
            System.err.println("[$idx] Testing finished, result=${(results[idx] * 100.0 / testRounds).format(2)}%")
        }
        System.err.println("Results: ${results.map { (it * 100.0 / testRounds).format(2) + '%' }}")
    }
    RLBotConfig.defaultFile.writeBytes(configBackup)
    return results
}

fun evaluateQTableTraining(trainingRounds: Int = 500, testRounds: Int = 100) {
    val results = testQTableTraining("sample.RamFire", trainingRounds, testRounds, 25)
    val buffer = ByteBuffer.allocate((results.size + 1) * Int.SIZE_BYTES)
    buffer.putInt(testRounds)
    results.forEach { buffer.putInt(it) }
    plotRoot.resolve("qtable-training.bin").writeBytes(buffer.array())
}

val defaultConfig = RLBotConfig(
    "QLearning",
    DqnInterface::class.simpleName!!,
    0.1,
    0.9,
    0.2F,
    true,
    1e-4F,
    0.2F,
    60,
    "Sigmoid",
    32,
    "robots/QTable.bin",
    "robots/DQN.bin"
)

fun <T> testHyperParameter(paramName: String, values: List<T>, rounds: Int, batch: Int = 1, config: (RLBotConfig.() -> Unit)? = null): List<Pair<T, FloatArray>> {
    val conf = defaultConfig.copy()
    config?.invoke(conf)
    val results = Array(values.size) { values[it] to FloatArray(rounds) }
    val property = (RLBotConfig::class.memberProperties.find { it.name == paramName } ?: throw IllegalArgumentException("Property not found"))
        as? KMutableProperty<*> ?: throw IllegalArgumentException("Property is not mutable")
    repeat(batch) { batchIdx ->
        System.err.println("\nBatch Testing $batchIdx\n")
        for ((index, value) in values.withIndex()) {
            property.setter.call(conf, value)
            conf.recordFileName = "robots/HyperParamTest-$currentTimeString.bin"
            conf.dqnFile?.delete()
            evaluateRLBot(conf, "sample.RamFire", rounds)
            val record = parseRecordFile(conf.recordFile!!).let(::toWinRate)
            val target = results[index].second
            record.indices.forEach { target[it] += record[it] }
            conf.recordFile!!.delete()
            System.err.println("$paramName=$value: ${(record.last() * 100).format(2)}%")
        }
    }
    if (batch > 1) {
        val divider = batch.toFloat()
        results.forEach { (_, winRate) -> winRate.indices.forEach { winRate[it] /= divider } }
    }
    return results.toList()
}

fun evaluateHiddenNeurons(rounds: Int = 400, batch: Int = 1) {
    val neuronCounts = listOf(5, 15, 30, 60, 90, 120)
    val results = testHyperParameter(RLBotConfig::nnHiddenLayerNeuronCount.name, neuronCounts, rounds, batch)
    val buffer = ByteBuffer.allocate(Int.SIZE_BYTES * 2 + neuronCounts.size * (Int.SIZE_BYTES + rounds * Float.SIZE_BYTES))
    buffer.putInt(rounds).putInt(neuronCounts.size)
    results.forEach { (param, winRate) ->
        buffer.putInt(param)
        buffer.putArray(winRate)
    }
    plotRoot.resolve("hidden-neurons.bin").writeBytes(buffer.array())
}

fun evaluateLearningRate(rounds: Int = 400, batch: Int = 1) {
    val learningRates = listOf(1e-5, 5e-5, 1e-4, 5e-4, 1e-3, 5e-3, 1e-2).map { it.toFloat() }
    val results = testHyperParameter(RLBotConfig::nnLearningRate.name, learningRates, rounds, batch)
    val buffer = ByteBuffer.allocate(Int.SIZE_BYTES * 2 + learningRates.size * (Int.SIZE_BYTES + rounds * Float.SIZE_BYTES))
    buffer.putInt(rounds).putInt(learningRates.size)
    results.forEach { (param, winRate) ->
        buffer.putFloat(param)
        buffer.putArray(winRate)
    }
    plotRoot.resolve("learning-rate.bin").writeBytes(buffer.array())
}

fun evaluateMomentum(rounds: Int = 400, batch: Int = 1) {
    val momentums = listOf(0.0, 0.1, 0.2, 0.35, 0.5, 0.65, 0.8, 0.9, 1.0).map { it.toFloat() }
    val results = testHyperParameter(RLBotConfig::nnMomentum.name, momentums, rounds, batch)
    val buffer = ByteBuffer.allocate(Int.SIZE_BYTES * 2 + momentums.size * (Int.SIZE_BYTES + rounds * Float.SIZE_BYTES))
    buffer.putInt(rounds).putInt(momentums.size)
    results.forEach { (param, winRate) ->
        buffer.putFloat(param)
        buffer.putArray(winRate)
    }
    plotRoot.resolve("momentum.bin").writeBytes(buffer.array())
}

fun evaluateDiscountFactor(rounds: Int = 400, batch: Int = 1) {
    val discountFactors = listOf(0.0, 0.01, 0.1, 0.2, 0.35, 0.5, 0.65, 0.8, 0.9, 0.99, 1.0)
    val results = testHyperParameter(RLBotConfig::rlDiscountFactor.name, discountFactors, rounds, batch)
    val buffer = ByteBuffer.allocate(Int.SIZE_BYTES * 2 + discountFactors.size * (Double.SIZE_BYTES + rounds * Float.SIZE_BYTES))
    buffer.putInt(rounds).putInt(discountFactors.size)
    results.forEach { (param, winRate) ->
        buffer.putDouble(param)
        buffer.putArray(winRate)
    }
    plotRoot.resolve(".discount-factor.bin").writeBytes(buffer.array())
}

fun evaluateBatchUpdate(rounds: Int = 400) {
    val batchUpdates = listOf(1, 4, 16, 64).let { it + it + it + it }
    val results = testHyperParameter(RLBotConfig::dqnBatchUpdate.name, batchUpdates, rounds)
    val buffer = ByteBuffer.allocate(Int.SIZE_BYTES * 2 + batchUpdates.size * (Int.SIZE_BYTES + rounds * Float.SIZE_BYTES))
    buffer.putInt(rounds).putInt(results.size)
    results.forEach { (param, winRate) ->
        buffer.putInt(param)
        buffer.putArray(winRate)
    }
    plotRoot.resolve("batch-update.bin").writeBytes(buffer.array())
}

fun main() {
    evaluateQTableTraining()
    evaluateHiddenNeurons()
    evaluateLearningRate()
    evaluateMomentum()
    evaluateDiscountFactor()
    evaluateBatchUpdate()
}