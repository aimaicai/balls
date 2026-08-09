package com.hyperionsoftware.balls.game

object GameConfig {
    const val WORLD_WIDTH = 6000f
    const val WORLD_HEIGHT = 6000f

    const val BASE_RADIUS = 40f
    const val MAX_RADIUS = 260f

    // A blob must be at least this many times bigger (by radius) to absorb another.
    const val ABSORB_RATIO = 1.2f

    const val PLAYER_BASE_SPEED = 220f
    const val BOT_BASE_SPEED = 190f

    const val DEFLATE_GRACE_SECONDS = 5f
    const val DEFLATE_RATE_PER_SECOND = 0.03f

    const val POWERUP_MAX_COUNT = 12
    const val POWERUP_SPAWN_MIN_SECONDS = 3f
    const val POWERUP_SPAWN_MAX_SECONDS = 6f
    const val POWERUP_RADIUS = 16f

    const val POWERUP_SPEED_MULTIPLIER = 2f
    const val POWERUP_SPEED_DURATION = 6f
    const val POWERUP_INVISIBILITY_DURATION = 8f
    const val POWERUP_GROWTH_MULTIPLIER = 2f

    // How far off-screen the danger/prey edge indicator can still "see".
    const val AWARENESS_RADIUS = 2500f

    // Bots widen their vision the fewer blobs remain alive, up to this multiplier,
    // so the endgame stays lively instead of everyone wandering aimlessly.
    const val BOT_MAX_AGGRESSION_BONUS = 1.2f

    const val COUNTDOWN_SECONDS = 3f
}
