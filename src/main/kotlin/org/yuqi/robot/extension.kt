package org.yuqi.robot

import robocode.Bullet
import robocode.Rules

val Bullet.damage: Double
    get() = Rules.getBulletDamage(power)

val Bullet.bonus: Double
    get() = Rules.getBulletHitBonus(power)