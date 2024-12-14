import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.kandy.dsl.continuous
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.ir.Plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points
import org.jetbrains.kotlinx.kandy.util.color.Color
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.yuqi.parseRecordFile
import org.yuqi.plotRoot
import org.yuqi.toWinRate
import org.yuqi.util.getArray
import java.nio.ByteBuffer
import kotlin.test.Test

class PlotTest {
    companion object {
        fun savePlot(plot: Plot, fileName: String) {
            plot.save(plotRoot.resolve(fileName).absolutePath)
        }
    }

    @Test
    fun generateSinglePlots() {
        val files = plotRoot.listFiles()!!.filter { it.extension == "bin" }
        for (file in files) {
            val fileName = file.nameWithoutExtension
            val winRate = parseRecordFile(file).let(::toWinRate)
            dataFrameOf(
                "Round" to winRate.indices.toList(),
                "Win Rate" to winRate.toList()
            ).plot {
                line {
                    x("Round")
                    y("Win Rate") {
                        scale = continuous(0.0F..1.0F)
                    }
                    color("Win Rate") {
                        scale = continuous(Color.RED..Color.GREEN)
                    }
                }
                layout {
                    size = 1024 to 768
                }
            }.let { savePlot(it, "$fileName.png") }
        }
    }

    @Test
    fun generateQTableTrainingPlot() {
        val file = plotRoot.resolve("qtable-training.bin")
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes)
        val testRounds = buffer.getInt()
        val results = IntArray(bytes.size / Int.SIZE_BYTES - 1) { buffer.getInt() }
        val winRates = results.map { it.toDouble() / testRounds }
        dataFrameOf(
            "Index" to winRates.indices.toList(),
            "Win Rate" to winRates
        ).plot {
            points {
                x("Index")
                y("Win Rate") {
                    scale = continuous(0.0..1.0)
                }
                size = 5.0
                color("Win Rate") {
                    scale = continuous(Color.RED..Color.GREEN)
                }
            }
            layout {
                size = 1024 to 768
            }
        }.let { savePlot(it, "qtable-training.png") }
    }

    @ParameterizedTest
    @CsvSource(
        "hidden-neurons,Int,Hidden Neurons,false",
        "learning-rate,Float,Learning Rate,false",
        "momentum,Float,Momentum,false",
        "discount-factor,Double,Discount Factor,false",
        "batch-update,Int,Batch Update,false"
    )
    fun generateHyperParamEvaluationPlot(fileName: String, paramType: String, title: String, continuousColor: Boolean) {
        val file = plotRoot.resolve("$fileName.bin")
        val buffer = ByteBuffer.wrap(file.readBytes())
        val testRounds = buffer.int
        val paramValueCount = buffer.int
        val winRates = mutableListOf<Pair<Any, FloatArray>>()
        repeat(paramValueCount) {
            val paramValue = when (paramType) {
                "Double" -> buffer.double
                "Float"  -> buffer.float
                "Int"    -> buffer.int
                else     -> error("Unsupported parameter type: $paramType")
            }
            val winRate = FloatArray(testRounds)
            buffer.getArray(winRate)
            winRates.add(paramValue to winRate)
        }
        plot {
            winRates.forEach { (param, rates) ->
                val colors = if (continuousColor) List(rates.size) { param } else List(rates.size) { param.toString() }
                line {
                    x(rates.indices) {
                        axis.name = "Round"
                    }
                    y(rates.toList()) {
                        axis.name = "Win Rate"
                        scale = continuous(0.0F..1.0F)
                    }
                    color(colors) {
                        legend.name = title
                        if (continuousColor)
                            scale = continuous(Color.RED..Color.GREEN)
                    }
                }
            }
            layout {
                size = 1024 to 768
            }
        }.let { savePlot(it, "$fileName.png") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["WinFactor"])
    fun generateWinFactorPlot(fileName: String) {
        val file = plotRoot.resolve("$fileName.bin")
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes)
        val winFactors = DoubleArray(bytes.size / Double.SIZE_BYTES) { buffer.double }
        for (i in 1 until winFactors.size)
            winFactors[i] += winFactors[i - 1]
        for (i in winFactors.indices)
            winFactors[i] /= i + 1.0
        dataFrameOf(
            "Round" to winFactors.indices.toList(),
            "Win Factor" to winFactors.toList()
        ).plot {
            line {
                x("Round")
                y("Win Factor")
                color("Win Factor") {
                    scale = continuous(Color.RED..Color.GREEN)
                }
            }
            layout {
                size = 1024 to 768
            }
        }.let { savePlot(it, "$fileName.png") }
    }
}