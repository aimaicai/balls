package com.hyperionsoftware.balls.game

enum class PowerUpType {
    // SPEED and INVISIBILITY are carried now too (see Blob.applyPowerUp): stored in the
    // single item slot and spent on demand via the same button as REPEL/FREEZE, instead of
    // applying - and potentially wasting their duration - the instant they're picked up.
    SPEED, GROWTH, INVISIBILITY, SHIELD, REPEL, FREEZE,

    // Permanent, instant-on-pickup stat increases (not carried). Each one closes part of
    // the remaining gap to its own cap, so early pickups matter more than later ones and
    // no one stacks these into an unbounded advantage.
    SPEED_UP, AGILITY_UP
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
