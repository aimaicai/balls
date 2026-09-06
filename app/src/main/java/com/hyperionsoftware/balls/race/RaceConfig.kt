package com.hyperionsoftware.balls.race

// Tunable numbers for Grand Prix mode - deliberately its own object rather than reusing
// GameConfig's, even though several values below intentionally match it for a familiar feel.
// GameConfig's world size/absorb ratio/etc. are mutable globals reconfigured per match by the
// classic mode's own arena-size setting (see GameConfig.applyArenaSize) - sharing them here
// would risk the two modes' engines quietly stomping on each other's tuning. A genuinely
// separate object keeps Grand Prix from ever being able to affect classic mode at all, and
// vice versa.
object RaceConfig {
    const val BASE_RADIUS = 40f
    const val MAX_RADIUS = 260f
    const val ABSORB_RATIO = 1.2f
    const val ZONE_DEATH_RADIUS = 10f

    // The race world is a fixed size for every track - smaller than classic mode's arena
    // since a circuit is meant to feel like a loop you keep lapping, not an open map to roam.
    const val WORLD_WIDTH = 4200f
    const val WORLD_HEIGHT = 3000f

    const val PLAYER_BASE_SPEED = 280f
    const val BOT_BASE_SPEED = 240f
    const val TURN_RATE_RADIANS_PER_SECOND = 1.6f

    // Balloons leak air everywhere, all the time, same principle as classic mode's safe
    // zone - just on-track/off-track instead of in/out of a shrinking circle. Off-track is
    // meant to be a real penalty (see RaceTrack.distanceOffTrack), not an instant kill,
    // since a moment spent cutting a corner should cost size, not the match outright.
    const val ON_TRACK_DEFLATE_RATE_PER_SECOND = 0.015f
    const val OFF_TRACK_DEFLATE_RATE_PER_SECOND = 0.12f

    // How far past the track's own half-width a blob can stray before it actually counts as
    // off-track - "leggermente" (slightly) off is fine, it's cutting a corner outright that
    // should cost something.
    const val OFF_TRACK_MARGIN = 90f

    // How close to a checkpoint's own waypoint counts as having reached it. Generous enough
    // that clipping past at speed on a wide part of the track still registers.
    const val CHECKPOINT_RADIUS = 260f

    const val BOOST_DRAIN_RATE_PER_SECOND = 0.25f
    const val BOOST_SPEED_MULTIPLIER = 1.6f
    const val BOOST_MAX_SPEED_MULTIPLIER = 2.6f
    const val BOOST_RAMP_UP_SECONDS = 4f
    const val BOT_SPRINT_MIN_RADIUS = 22f

    // Power-up radius itself comes from the reused game.PowerUp class (GameConfig.
    // POWERUP_RADIUS/SUPPLY_DROP_RADIUS_MULTIPLIER, both plain constants - no risk of
    // picking up classic mode's mutable arena-size state by reusing that one class).
    const val POWERUP_GROWTH_RADIUS_BONUS = 20f
    const val POWERUP_SPEED_DURATION = 4f
    const val POWERUP_SPEED_MULTIPLIER = 2f
    const val POWERUP_INVISIBILITY_DURATION = 2f
    const val POWERUP_SHIELD_DURATION = 12f

    const val REPEL_RANGE_MULTIPLIER = 6f
    const val REPEL_FORCE = 260f
    const val FREEZE_RANGE_MULTIPLIER = 5f
    const val FREEZE_DURATION_SECONDS = 1.8f
    const val HOOK_RANGE_MULTIPLIER = 6f
    const val HOOK_FORCE = 320f

    const val PERMANENT_STAT_TIER_COUNT = 5
    const val PERMANENT_SPEED_MAX_MULTIPLIER = 1.5f
    const val PERMANENT_TURN_RATE_MAX_MULTIPLIER = 3f
    const val PERMANENT_POTENCY_MAX_MULTIPLIER = 1.8f

    // A little starting-size variance breaks the "everyone's identical, nobody can absorb
    // anybody" opening stalemate, same reasoning as classic mode - the player still starts
    // at a fair baseRadius, only bots get this.
    const val BOT_START_SIZE_MIN_FACTOR = 0.8f
    const val BOT_START_SIZE_MAX_FACTOR = 1.25f

    // Comfortably bigger than two max-size spawning bots' radii combined so nobody starts
    // touching, let alone overlapping - same idea as GameConfig.MIN_SPAWN_SEPARATION.
    const val MIN_SPAWN_SEPARATION = BASE_RADIUS * 3f
    const val SPAWN_PLACEMENT_MAX_ATTEMPTS = 20

    // How far a bot notices threats/prey/power-ups - unlike classic mode this never widens
    // late-match, since a race doesn't have a shrinking zone forcing survivors together.
    const val BOT_VISION_RADIUS = 900f

    const val POWERUP_MAX_COUNT = 10
    const val POWERUP_SPAWN_INTERVAL_SECONDS = 6f

    const val MIN_LAPS = 1
    const val MAX_LAPS = 8
    const val DEFAULT_LAPS = 3

    const val MIN_BOT_COUNT = 1
    const val MAX_BOT_COUNT = 12
    const val DEFAULT_BOT_COUNT = 7

    const val COUNTDOWN_SECONDS = 3f
}
