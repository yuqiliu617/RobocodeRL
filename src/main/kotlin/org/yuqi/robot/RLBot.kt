package org.yuqi.robot

import org.yuqi.robot.bridge.OpponentStatus
import org.yuqi.robot.bridge.RawState
import org.yuqi.robot.bridge.State
import org.yuqi.robot.bridge.Status
import org.yuqi.util.Point
import org.yuqi.util.PolarPoint
import org.yuqi.util.normalizeRadian
import robocode.*
import java.awt.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

class RLBot : AdvancedRobot() {
    enum class TrackStatus {
        Init,
        OnTrack,
        OffTrack
    }

    companion object {
        private val trackOffset = (Rules.RADAR_TURN_RATE_RADIANS - Rules.GUN_TURN_RATE_RADIANS - Rules.MAX_TURN_RATE_RADIANS) * 0.75
        private var winRecords = mutableListOf<Int>()
        fun radianTransform(rawRadian: Double) = normalizeRadian(Math.PI / 2 - rawRadian, true)
    }

    private var track: TrackStatus = TrackStatus.Init

    init {
        env.interactEventListeners.add { it.applyTo(this) }
        RawState.resetGlobal()
        rl.start()
    }

    private fun gunTrack(target: Point) {
        val relative = target - Point(x, y)
        val heading = radianTransform(gunHeadingRadians)
        val bodyTurn = -turnRemaining.sign * Rules.getTurnRateRadians(velocity)
        val deltaRadian = normalizeRadian(relative.radian - heading - bodyTurn)
        setTurnGunLeftRadians(max(min(deltaRadian, Rules.GUN_TURN_RATE_RADIANS), -Rules.GUN_TURN_RATE_RADIANS))
    }

    private fun radarTrack(target: Point) {
        val relative = target - Point(x, y)
        val heading = radianTransform(radarHeadingRadians)
        val deltaRadian = normalizeRadian(relative.radian - heading)
        if (track != TrackStatus.OnTrack)
            setTurnRadarLeftRadians(if (deltaRadian > 0) Double.MAX_VALUE else -Double.MAX_VALUE)
        else {
            val turnRadians = deltaRadian +
                max(min(gunTurnRemainingRadians, Rules.GUN_TURN_RATE_RADIANS), -Rules.GUN_TURN_RATE_RADIANS) +
                turnRemaining.sign * Rules.getTurnRateRadians(velocity) +
                trackOffset * deltaRadian.sign
            setTurnRadarLeftRadians(turnRadians)
        }
    }

    override fun run() {
        setAllColors(Color.CYAN)
        setTurnRadarRightRadians(Double.MAX_VALUE)
    }

    override fun onStatus(e: StatusEvent) {
        val scannedEvent = scannedRobotEvents.firstOrNull()
        if (scannedEvent != null) {
            val oldTrack = track
            track = TrackStatus.OnTrack
            val status = Status(e.status)
            val scannedTarget = scannedEvent.let {
                OpponentStatus(
                    it.name,
                    status.position + PolarPoint(it.distance, status.heading - it.bearingRadians).toPoint(),
                    radianTransform(it.headingRadians),
                    it.energy,
                    it.velocity
                )
            }
            val state = State(this, status, scannedTarget, allEvents.filter { it !is ScannedRobotEvent })
            if (oldTrack == TrackStatus.Init)
                env.onStart(state)
            else
                env.onUpdate(state)
            gunTrack(scannedTarget.position)
            radarTrack(scannedTarget.position)
        } else {
            if (track != TrackStatus.Init)
                gunTrack(env.curState!!.opponent.position)
            if (track == TrackStatus.OnTrack) {
                track = TrackStatus.OffTrack
                radarTrack(env.curState!!.opponent.position)
            }
        }
    }

    override fun onRoundEnded(event: RoundEndedEvent) {
        env.onFinish(State(this, Status(this), env.curState!!.opponent, listOf(event)))
        RawState.resetGlobal()
    }

    override fun onBattleEnded(event: BattleEndedEvent) {
        rl.stop()
        table.save()
        config.recordFile?.let { file ->
            val roundResults = ByteArray(numRounds)
            winRecords.forEach { roundResults[it] = 1 }
            file.writeBytes(roundResults)
        }
    }

    override fun onWin(event: WinEvent) {
        winRecords.add(roundNum)
    }
}