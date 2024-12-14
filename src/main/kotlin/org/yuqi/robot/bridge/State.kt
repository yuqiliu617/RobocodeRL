package org.yuqi.robot.bridge

import org.yuqi.rl.IIndexableStatic
import org.yuqi.rl.IState
import org.yuqi.robot.bonus
import org.yuqi.robot.damage
import org.yuqi.util.*
import robocode.*
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.sqrt

open class RawState(
    val robot: Robot,
    val status: Status,
    val opponent: OpponentStatus,
    val events: List<Event>
) {
    companion object {
        private var totalBulletDamage: Double = 0.0
        private var totalRamDamage: Double = 0.0
        private var deathBonusCollected: Boolean = false
        fun resetGlobal() {
            totalRamDamage = 0.0
            totalBulletDamage = 0.0
            deathBonusCollected = false
        }
    }

    val reward
        get(): Float {
            events.firstOrNull { it is RoundEndedEvent }?.let {
                val ev = it as RoundEndedEvent
                val base = when {
                    robot.energy <= 0.0    -> -200
                    opponent.energy <= 0.0 -> 200
                    else                   -> 100
                }

                val avgTurns = ev.totalTurns.toDouble() / (status.roundNum + 1)
                val timeBonus = avgTurns + 50 / (ev.turns + 50)
                val energyRatio = (robot.energy + 20) / (opponent.energy + 20).let { v -> if (v >= 1) v else 1 / v }
                return (timeBonus * energyRatio * base).toFloat()
            }
            val damageFactor = 4.0
            var hitRobot = false
            var r = events.sumOf {
                when (it) {
                    is BulletHitEvent    -> (it.bullet.damage + it.bullet.bonus - it.bullet.power) * damageFactor
                    is BulletMissedEvent -> -it.bullet.power * damageFactor
                    is HitByBulletEvent  -> (it.bullet.power - it.bullet.damage - it.bullet.bonus) * damageFactor
                    is HitRobotEvent     -> {
                        hitRobot = true
                        damageFactor * Rules.ROBOT_HIT_DAMAGE * ((status.energy - it.energy).sign + if (it.isMyFault) 1 else -1)
                    }

                    is HitWallEvent      -> -1.0 * (if (hitRobot) 10.0 else 1.0)
                    else                 -> 0.0
                }
            }
            r += (status.energy - opponent.energy) / 100
            return r.toFloat()
        }

    fun getScore(): Float {
        events.firstOrNull { it is RoundEndedEvent }?.let {
            (if (status.energy > 0.0) 50F else 0F) +
                if (status.opponentsCount == 0) 10F else 0F
        }
        return events.sumOf {
            when (it) {
                is BulletHitEvent -> {
                    totalBulletDamage += it.bullet.damage
                    it.bullet.damage + (if (it.energy <= 0.0) totalBulletDamage * 0.2 else 0.0)
                }

                is HitRobotEvent  -> {
                    totalRamDamage += Rules.ROBOT_HIT_DAMAGE
                    Rules.ROBOT_HIT_DAMAGE * 2 + (if (it.energy <= 0.0) totalRamDamage * 0.3 else 0.0)
                }

                else              -> 0.0
            }
        }.toFloat()
    }

    fun distanceToWalls(p: Point = status.position): WallDistance = with(robot) {
        val radius = sqrt(2.0) / 2 * width
        return WallDistance(
            battleFieldHeight - p.y - radius,
            p.y - radius,
            p.x - radius,
            battleFieldWidth - p.x - radius
        )
    }
}

class State(
    robot: Robot,
    status: Status,
    opponent: OpponentStatus,
    events: List<Event>
) : RawState(robot, status, opponent, events), IState {
    companion object : IIndexableStatic {
        val energyLevels = sequence {
            var value = 1
            while (true) {
                val level = value * Rules.getBulletDamage(Rules.MAX_BULLET_POWER)
                if (level >= 100.0)
                    break
                yield(level)
                value = value shl 1
            }
        }.toList()
        val distanceLevels = sequence {
            val base = sqrt(2.0) * 36 // tank diameter
            var value = 1
            repeat(2) {
                yield(base + value * Rules.MAX_VELOCITY)
                value = value shl 2
            }
            yield(value * Rules.MAX_VELOCITY)
        }.toList()
        private val maxValues: List<Int> = listOf(energyLevels.size + 1, 3, 2, energyLevels.size + 1, 4, 3, 2, distanceLevels.size + 1, 4, 2)
        override val maxIndex: Int = maxValues.product()
    }

    val bearing: PolarPoint = (opponent.position - status.position).toPolarPoint()

    /**
     * |energy|velocity|hitwall|op.energy|op.rheading|op.velocity|op.hitwall|dist|bearing|gunheat|
     * |  4   |   3    |   2   |    4    |     4     |     3     |    2     |  4 |   4   |   2   |
     */
    override val index: Int
        get() = encodeIndex(
            listOf(
                energyLevels.indexOfFirst { status.energy < it }.let { if (it == -1) energyLevels.size else it },
                if (status.velocity == 0.0) 0 else if (status.velocity == Rules.MAX_VELOCITY) 2 else 1,
                events.any { it is HitWallEvent }.toInt(),
                energyLevels.indexOfFirst { opponent.energy < it }.let { if (it == -1) energyLevels.size else it },
                floor(normalizeRadian(opponent.heading - status.heading, true) / Math.PI * 2).toInt(),
                if (opponent.velocity == 0.0) 0 else if (opponent.velocity == Rules.MAX_VELOCITY) 2 else 1,
                distanceToWalls(opponent.position).run { up <= 0.0 || down <= 0.0 || left <= 0.0 || right <= 0.0 }.toInt(),
                distanceLevels.indexOfFirst { bearing.length < it }.let { if (it == -1) distanceLevels.size else it },
                floor(normalizeRadian(bearing.radian - status.heading, true) / Math.PI * 2).toInt(),
                (status.gunHeat == 0.0).toInt()
            ), maxValues
        )
}

data class OpponentStatus(
    val name: String,
    val position: Point,
    val heading: Double,
    val energy: Double,
    val velocity: Double
)

data class WallDistance(
    val up: Double,
    val down: Double,
    val left: Double,
    val right: Double
)