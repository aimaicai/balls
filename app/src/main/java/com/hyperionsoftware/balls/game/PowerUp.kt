package com.hyperionsoftware.balls.game

enum class PowerUpType {
    SPEED, GROWTH, INVISIBILITY, SHIELD
}

class PowerUp(
    val type: PowerUpType,
    val position: Vector2
) {
    // SHIELD only ever comes from a supply drop, rendered bigger so it reads as a rarer,
    // more valuable pickup than a regular power-up.
    val radius: Float = if (type == PowerUpType.SHIELD) {
        GameConfig.POWERUP_RADIUS * GameConfig.SUPPLY_DROP_RADIUS_MULTIPLIER
    } else {
        GameConfig.POWERUP_RADIUS
    }
    var collected: Boolean = false
}
