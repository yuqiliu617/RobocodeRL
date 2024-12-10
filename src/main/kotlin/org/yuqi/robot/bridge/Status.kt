package org.yuqi.robot.bridge

import org.yuqi.robot.RLBot
import org.yuqi.util.Point
import robocode.Robot
import robocode.RobotStatus

data class Status(
    val time: Long,
    val position: Point,
    val energy: Double,
    val velocity: Double,
    val heading: Double,
    val gunHeading: Double,
    val radarHeading: Double,
    val gunHeat: Double,
    val roundNum: Int,
    val roundCount: Int,
    val opponentsCount: Int
) {
    constructor(status: RobotStatus) : this(
        status.time,
        Point(status.x, status.y),
        status.energy,
        status.velocity,
        RLBot.radianTransform(status.headingRadians),
        RLBot.radianTransform(status.gunHeadingRadians),
        RLBot.radianTransform(status.radarHeadingRadians),
        status.gunHeat,
        status.roundNum,
        status.numRounds,
        status.others
    )

    constructor(robot: Robot) : this(
        robot.time,
        Point(robot.x, robot.y),
        robot.energy,
        robot.velocity,
        RLBot.radianTransform(robot.heading / 180 * Math.PI),
        RLBot.radianTransform(robot.gunHeading / 180 * Math.PI),
        RLBot.radianTransform(robot.radarHeading / 180 * Math.PI),
        robot.gunHeat,
        robot.roundNum,
        robot.numRounds,
        robot.others
    )
}