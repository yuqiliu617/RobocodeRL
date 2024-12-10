package org.yuqi.robot

import org.yuqi.rl.Environment
import org.yuqi.rl.algorithm.SARSA
import org.yuqi.robot.bridge.*
import org.yuqi.util.*
import robocode.*
import java.awt.Color
import java.nio.file.Paths
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

        val env = Environment<State, Action>()
        val dataFile = Paths.get("robots/rl.bin").toFile()
        val table = ArrayTable(State.maxIndex, Action.maxIndex, dataFile, true).apply {
            if (file.exists())
                runCatching { load() }
        }
        val lambda: Float = 0.1F
        val rl = SARSA(env, table, 0.2, 0.9, Action::fromIndex, lambda).apply { start() }
    }

    private var track: TrackStatus = TrackStatus.Init

    init {
        env.interactEventListeners.add { it.applyTo(this) }
        RawState.resetGlobal()
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
        val fileName = "${rl::class.java.typeName.let { it.substring(it.lastIndexOf(".") + 1) }}-${rl.learningRate}-${rl.decayFactor}-${lambda}"
        Paths.get("../RobocodeRL/plot/$fileName.txt").toFile()
            .writeText("$numRounds,${winRecords.joinToString(",")}")
    }

    override fun onWin(event: WinEvent) = winRecords.add(roundNum).discard()
}