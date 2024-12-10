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

    val reward: Float
        get() {
            events.firstOrNull { it is RoundEndedEvent }?.let {
                val ev = it as RoundEndedEvent
                val base = when {
                    robot.energy <= 0.0    -> -200
                    opponent.energy <= 0.0 -> 200
                    else                   -> 100
                }
                val avgTurns = ev.totalTurns.toDouble() / (status.roundNum + 1)
                val timeBonus = exp((avgTurns - ev.turns) / 100.0)
                return (timeBonus * ((robot.energy + 20) / (opponent.energy + 20)) * base).toFloat()
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

    val score: Float
        get() {
            events.firstOrNull { it is RoundEndedEvent }?.let {
                (if (status.energy > 0.0) 50F else 0F) +
                    if (status.opponentsCount == 0) 10F else 0F
            }
            var s = events.sumOf {
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
            }
            return s.toFloat()
        }

    fun distanceToWall(dir: Direction): Double = with(robot) {
        val radius = sqrt(2.0) / 2 * width
        return when (dir) {
            Direction.Up    -> battleFieldHeight - y
            Direction.Down  -> y
            Direction.Left  -> x
            Direction.Right -> battleFieldWidth - x
        } - radius
    }
}

class State(
    robot: AdvancedRobot,
    status: Status,
    targetStatus: OpponentStatus,
    events: List<Event>
) : RawState(robot, status, targetStatus, events), IState {
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
        val maxBrakeDistance = Rules.MAX_VELOCITY * (Rules.MAX_VELOCITY / Rules.DECELERATION) / 2
        private val maxValues: List<Int> = listOf(energyLevels.size + 1, 3, 2, 2, distanceLevels.size + 1, 4, energyLevels.size + 1, 4, 3)
        override val maxIndex: Int = maxValues.product()
    }

    val bearing: PolarPoint = (opponent.position - status.position).toPolarPoint()

    fun radianToOpponent(radian: Double) = normalizeRadian(bearing.radian - radian, true)

    /**
     * |energy|velocity|gunheat|hitwall|dist|bearing|op.energy|op.heading|op.velocity|
     * |  4   |   3    |   2   |   2   |  4 |   4   |    4    |    4     |     3     |
     */
    override val index: Int
        get() {
            val energyLevel = energyLevels.indexOfFirst { status.energy < it }.let { if (it == -1) energyLevels.size else it }
            val velocityFlag = if (status.velocity == 0.0) 0 else if (status.velocity == Rules.MAX_VELOCITY) 2 else 1
            val gunHeatFlag = if (status.gunHeat == 0.0) 1 else 0
            //val gunRangeFlag = if (abs(radianToOpponent(status.gunHeading)) <= Rules.GUN_TURN_RATE_RADIANS) 1 else 0
            val hitWallFlag = if (events.any { it is HitWallEvent }) 1 else 0
            // Direction.entries.firstOrNull { distanceToWall(it) < maxBrakeDistance }?.ordinal ?: 0
            val distanceLevel = distanceLevels.indexOfFirst { bearing.length < it }.let { if (it == -1) distanceLevels.size else it }
            val bearingLevel = floor(radianToOpponent(status.heading) / Math.PI * 2).toInt()
            val opEnergyLevel = energyLevels.indexOfFirst { opponent.energy < it }.let { if (it == -1) energyLevels.size else it }
            val opHeadingLevel = floor((opponent.heading - status.heading) / Math.PI * 2).toInt()
            val opVelocityFlag = if (opponent.velocity == 0.0) 0 else if (opponent.velocity == Rules.MAX_VELOCITY) 2 else 1
            return encodeIndex(listOf(energyLevel, velocityFlag, gunHeatFlag, hitWallFlag, distanceLevel, bearingLevel, opEnergyLevel, opHeadingLevel, opVelocityFlag), maxValues)
        }
}

data class OpponentStatus(
    val name: String,
    val position: Point,
    val heading: Double,
    val energy: Double,
    val velocity: Double
)

enum class Direction {
    Up,
    Down,
    Left,
    Right
}
