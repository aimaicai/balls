package com.hyperionsoftware.balls.game

object GameConfig {
    const val WORLD_WIDTH = 6000f
    const val WORLD_HEIGHT = 6000f

    const val BASE_RADIUS = 40f
    const val MAX_RADIUS = 260f

    // A blob must be at least this many times bigger (by radius) to absorb another.
    const val ABSORB_RATIO = 1.2f

    const val PLAYER_BASE_SPEED = 280f
    const val BOT_BASE_SPEED = 240f

    const val DEFLATE_GRACE_SECONDS = 5f
    const val DEFLATE_RATE_PER_SECOND = 0.03f

    // Power-up density is user-selectable (1..30 from the main menu). At level N there can be
    // up to POWERUP_MAX_COUNT_PER_LEVEL * N power-ups alive at full safe-zone area, spawning
    // N times as often. The actual cap scales down with the zone's area as it shrinks (see
    // POWERUP_MIN_AREA_FACTOR), never dropping to zero so late-game ties can still be broken.
    const val POWERUP_MAX_COUNT_PER_LEVEL = 10
    const val POWERUP_MIN_AREA_FACTOR = 0.2f
    const val POWERUP_MIN_FREQUENCY_LEVEL = 1
    const val POWERUP_MAX_FREQUENCY_LEVEL = 30
    const val POWERUP_DEFAULT_FREQUENCY_LEVEL = 15
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

    // Shrinking safe zone: starts covering the whole map (so nobody takes damage at the
    // start) and closes in on the arena center over the match, forcing survivors together
    // instead of letting them wander a huge, empty-feeling map in the late game.
    const val SAFE_ZONE_INITIAL_RADIUS = 4300f
    const val SAFE_ZONE_MIN_RADIUS = 500f
    const val SAFE_ZONE_SHRINK_DURATION_SECONDS = 75f

    // Outside the zone, size decays with no floor at baseRadius anymore (unlike normal
    // deflation) and death follows below ZONE_DEATH_RADIUS: staying out is a real risk, not
    // just a nuisance. Getting back inside heals lost size back up to baseRadius.
    const val SAFE_ZONE_DAMAGE_RATE_PER_SECOND = 0.12f
    const val ZONE_DEATH_RADIUS = 1f
    const val ZONE_HEAL_RATE_PER_SECOND = 0.3f

    // Voluntary boost: holding the boost button burns size for extra speed, stopping once
    // size hits baseRadius (never below it - this is a deliberate trade, not a hazard).
    const val BOOST_DRAIN_RATE_PER_SECOND = 0.25f
    const val BOOST_SPEED_MULTIPLIER = 1.6f

    // Balloons are pushed by their own exhaust: a moving balloon blows a cone of air out its
    // back that shoves any other balloon caught in it further away. Range scales with the
    // source's own radius (a bigger balloon has a bigger nozzle).
    const val THRUST_RANGE_MULTIPLIER = 4f
    const val THRUST_CONE_MIN_ALIGNMENT = 0.3f
    const val THRUST_FORCE_PER_SECOND = 320f
}
