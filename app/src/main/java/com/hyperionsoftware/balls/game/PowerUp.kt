package com.hyperionsoftware.balls.game

enum class PowerUpType {
    SPEED, GROWTH, INVISIBILITY
}

class PowerUp(
    val type: PowerUpType,
    val position: Vector2
) {
    val radius: Float = GameConfig.POWERUP_RADIUS
    var collected: Boolean = false
}
