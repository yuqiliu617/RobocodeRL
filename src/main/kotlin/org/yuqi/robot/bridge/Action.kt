package org.yuqi.robot.bridge

import org.yuqi.rl.IIndexable
import org.yuqi.rl.IIndexableStatic
import robocode.AdvancedRobot
import robocode.Rules
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

enum class Action(val component: ActionComponent?, val value: Double) : IIndexable {
    Nothing(null, 0.0),
    Stop(ActionComponent.Engine, 0.0),
    Accelerate(ActionComponent.Engine, 1.0),
    Decelerate(ActionComponent.Engine, -1.0),
    SteerLeft(ActionComponent.SteeringGear, Rules.MAX_TURN_RATE_RADIANS),
    SteerLeftHalf(ActionComponent.SteeringGear, Rules.MAX_TURN_RATE_RADIANS / 2),
    SteerRight(ActionComponent.SteeringGear, -Rules.MAX_TURN_RATE_RADIANS),
    SteerRightHalf(ActionComponent.SteeringGear, -Rules.MAX_TURN_RATE_RADIANS / 2),
    FireMinPower(ActionComponent.Trigger, Rules.MIN_BULLET_POWER),
    FireMedPower(ActionComponent.Trigger, 1.0),
    FireHighPower(ActionComponent.Trigger, 2.0),
    FireMaxPower(ActionComponent.Trigger, Rules.MAX_BULLET_POWER);

    companion object : IIndexableStatic {
        override val maxIndex = entries.size

        fun fromIndex(index: Int): Action = entries[index]
    }

    override val index: Int get() = ordinal

    fun applyTo(robot: AdvancedRobot) = with(robot) {
        if (component == null)
            return
        when (component) {
            ActionComponent.Engine       -> setAhead(if (abs(value) < 0.1) 0.0 else value.sign * Double.MAX_VALUE)
            ActionComponent.SteeringGear -> setTurnLeftRadians(value.sign * min(abs(value), Rules.MAX_TURN_RATE_RADIANS))
            ActionComponent.Turret       -> setTurnGunLeftRadians(value.sign * min(abs(value), Rules.GUN_TURN_RATE_RADIANS))
            ActionComponent.Radar        -> setTurnRadarLeftRadians(value.sign * min(abs(value), Rules.RADAR_TURN_RATE_RADIANS))
            ActionComponent.Trigger      -> {
                if (gunHeat == 0.0)
                    setFire(min(Rules.MAX_BULLET_POWER, max(Rules.MIN_BULLET_POWER, value)))
            }
        }
    }
}

enum class ActionComponent {
    Engine,
    SteeringGear,
    Turret,
    Radar,
    Trigger
}