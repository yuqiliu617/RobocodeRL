package org.yuqi

import org.yuqi.robot.RLBot
import robocode.Robocode
import java.io.File
import java.nio.file.Paths

fun runRobocode(
    robots: Iterable<String>,
    roundCount: Int = 10,
    battlefieldWidth: Int = 800,
    battlefieldHeight: Int = 800,
    freshStart: Boolean = false
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
    if (freshStart)
        RLBot.dataFile.apply {
            if (exists()) {
                val newName = "$nameWithoutExtension-${System.currentTimeMillis()}.$extension"
                renameTo(File(parentFile, newName))
            }
        }
    Robocode.main(arrayOf("-battle", file.absolutePath))
}

fun main() {
    val bots = listOf("sample.RamFire", RLBot::class.java.name)
    val rounds = 4000
    runRobocode(bots, rounds, freshStart = true)
}