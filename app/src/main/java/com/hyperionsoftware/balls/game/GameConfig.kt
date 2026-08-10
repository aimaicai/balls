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

    // Size now drains away constantly just from existing (see the ambient leak below), so
    // GROWTH power-ups need to spawn noticeably more often than SPEED/INVISIBILITY or
    // survival becomes pure attrition instead of a fight. Weights, not percentages: GROWTH
    // is picked 3 times out of every 5 spawns.
    const val POWERUP_GROWTH_WEIGHT = 3
    const val POWERUP_SPEED_WEIGHT = 1
    const val POWERUP_INVISIBILITY_WEIGHT = 1

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

    // Balloons always leak air, everywhere, all the time - there is no truly "safe" state
    // anymore, only "slower". Inside the zone the leak is a slow background attrition;
    // outside it it's much faster. Either way, dropping below ZONE_DEATH_RADIUS deflates
    // the balloon for good and ends the match for it.
    const val AMBIENT_DEFLATE_RATE_PER_SECOND = 0.015f
    const val OUT_OF_ZONE_DEFLATE_RATE_PER_SECOND = 0.12f
    const val ZONE_DEATH_RADIUS = 1f

    // Voluntary boost/sprint: works everywhere, any time, stacking on top of the ambient
    // leak above and trading size for extra speed with no floor - burning all the way down
    // to ZONE_DEATH_RADIUS kills the balloon.
    const val BOOST_DRAIN_RATE_PER_SECOND = 0.25f
    const val BOOST_SPEED_MULTIPLIER = 1.6f

    // Bots only spend size sprinting during genuine emergencies (an imminent threat or
    // being caught outside the shrinking zone), never for routine chasing or wandering,
    // and only with a comfortable buffer left to spend - otherwise they burn themselves
    // out chasing/fleeing and never survive long enough to actually fight anyone.
    const val BOT_SPRINT_MIN_RADIUS = 22f

    // Below this fraction of baseRadius, a bot treats itself as running low and actively
    // hunts the nearest growth power-up to refill, instead of whatever's merely closest.
    const val BOT_LOW_SIZE_FRACTION = 0.7f

    // Balloons are pushed by their own exhaust: a moving balloon blows a cone of air out its
    // back that shoves any other balloon caught in it further away. Range scales with the
    // source's own radius (a bigger balloon has a bigger nozzle).
    const val THRUST_RANGE_MULTIPLIER = 4f
    const val THRUST_CONE_MIN_ALIGNMENT = 0.3f
    const val THRUST_FORCE_PER_SECOND = 320f

    // Movement is direct (joystick magnitude drives speed immediately), but direction
    // changes are gentle: facingDirection turns toward wherever it's aimed at this
    // bounded rate instead of snapping straight to the opposite heading.
    const val TURN_RATE_RADIANS_PER_SECOND = 2.5f
}
