package com.hyperionsoftware.balls.game

enum class PowerUpType {
    SPEED, GROWTH, INVISIBILITY, SHIELD, REPEL, FREEZE;

    // Carried items aren't applied on pickup - they're stored in a single slot (picking up
    // a new one replaces whatever's already held) and spent later with a dedicated button,
    // unlike the instant-effect types above.
    val isCarriedItem: Boolean
        get() = this == REPEL || this == FREEZE
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
