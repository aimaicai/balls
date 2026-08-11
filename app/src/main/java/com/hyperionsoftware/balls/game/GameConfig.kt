package com.hyperionsoftware.balls.game

object GameConfig {
    // Arena size is user-selectable from the main menu. WORLD_WIDTH/HEIGHT, the safe zone's
    // starting radius, and how long the zone takes to shrink through its stages all scale
    // together by the chosen tier's factor, applied via applyArenaSize() before a match
    // starts - a bigger arena also takes proportionally longer to close in, instead of
    // covering more ground in the same amount of time. SAFE_ZONE_MIN_RADIUS and the final
    // round's own tightening (SAFE_ZONE_FINAL_MIN_RADIUS/SAFE_ZONE_FINAL_SHRINK_SECONDS)
    // deliberately do not scale, so the endgame feels the same at every arena size.
    enum class ArenaSize(val scaleFactor: Float) {
        SMALL(0.65f),
        NORMAL(1f),
        LARGE(1.5f),
        HUGE(2.2f)
    }

    private const val BASE_WORLD_WIDTH = 6000f
    private const val BASE_WORLD_HEIGHT = 6000f
    private const val BASE_SAFE_ZONE_INITIAL_RADIUS = 4300f
    private const val BASE_SAFE_ZONE_STAGE_SHRINK_SECONDS = 18f

    var WORLD_WIDTH = BASE_WORLD_WIDTH
        private set
    var WORLD_HEIGHT = BASE_WORLD_HEIGHT
        private set

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

    // In the final round, a single GROWTH at the normal multiplier instantly cleared
    // ABSORB_RATIO against everyone else (freshly reset to the same base size), turning the
    // finale into "whoever reaches the first power-up wins". This weaker multiplier keeps
    // GROWTH worth fighting for without letting one pickup alone decide the match - it takes
    // two before the ratio crosses ABSORB_RATIO.
    const val POWERUP_GROWTH_MULTIPLIER_FINAL_ROUND = 1.15f

    // Size now drains away constantly just from existing (see the ambient leak below), so
    // GROWTH power-ups need to spawn noticeably more often than SPEED/INVISIBILITY or
    // survival becomes pure attrition instead of a fight. Weights, not percentages: GROWTH
    // is picked 3 times out of every 5 spawns. SHIELD is deliberately absent here - it only
    // ever comes from a supply drop (see below), never the regular random pool.
    const val POWERUP_GROWTH_WEIGHT = 3
    const val POWERUP_SPEED_WEIGHT = 1
    const val POWERUP_INVISIBILITY_WEIGHT = 1
    const val POWERUP_REPEL_WEIGHT = 1
    const val POWERUP_FREEZE_WEIGHT = 1
    const val POWERUP_SPEED_UP_WEIGHT = 1
    const val POWERUP_AGILITY_UP_WEIGHT = 1
    const val POWERUP_SHIELD_DURATION = 12f

    // REPEL: an instant burst that shoves everyone within range away from whoever used it.
    const val REPEL_RANGE_MULTIPLIER = 6f
    const val REPEL_FORCE = 260f

    // FREEZE: locks movement for everyone caught within range for a short window - long
    // enough to escape or catch up, short enough not to be oppressive.
    const val FREEZE_RANGE_MULTIPLIER = 5f
    const val FREEZE_DURATION_SECONDS = 1.8f

    // Permanent stat pickups (SPEED_UP/AGILITY_UP): each pickup advances one discrete tier
    // out of PERMANENT_STAT_TIER_COUNT, capped there, so a single pickup always fills
    // exactly one HUD pip and stacking stays bounded. Agility's cap is more generous than
    // speed's since a nimbler turn is a different kind of edge than raw power.
    const val PERMANENT_STAT_TIER_COUNT = 10
    const val PERMANENT_SPEED_MAX_MULTIPLIER = 1.5f
    const val PERMANENT_TURN_RATE_MAX_MULTIPLIER = 3f

    // A rare, more valuable pickup that always grants SHIELD, spawned on its own separate
    // timer (independent of the regular power-up cap/timer) and telegraphed with a pulsing
    // beacon so it draws a scramble instead of blending in with regular power-ups. At most
    // one is ever alive at a time.
    const val SUPPLY_DROP_MIN_SECONDS = 25f
    const val SUPPLY_DROP_MAX_SECONDS = 40f
    const val SUPPLY_DROP_RADIUS_MULTIPLIER = 1.8f

    // The opening minute used to feel slow: everyone started identical and the safe zone
    // covered nearly the whole map, so power-ups were sparse and nobody could absorb
    // anybody. A chunk of the match's power-up cap now spawns immediately instead of
    // trickling in from zero, and bots start at a random size around baseRadius so some
    // early pairs can already absorb each other on sight.
    const val POWERUP_INITIAL_FILL_FRACTION = 0.6f
    const val BOT_START_SIZE_MIN_FACTOR = 0.8f
    const val BOT_START_SIZE_MAX_FACTOR = 1.25f

    // How far off-screen the danger/prey edge indicator can still "see".
    const val AWARENESS_RADIUS = 2500f

    // Bots widen their vision the fewer blobs remain alive, up to this multiplier,
    // so the endgame stays lively instead of everyone wandering aimlessly.
    const val BOT_MAX_AGGRESSION_BONUS = 1.2f

    const val COUNTDOWN_SECONDS = 3f

    // Shrinking safe zone: starts covering the whole map (so nobody takes damage at the
    // start) and closes in on the arena center over the match, forcing survivors together
    // instead of letting them wander a huge, empty-feeling map in the late game. It shrinks
    // in stages rather than one continuous slide: a hold phase (during which the next,
    // smaller circle is telegraphed as a preview outline) followed by an active shrink
    // phase, repeated SAFE_ZONE_STAGE_COUNT times. Same total duration as the old single
    // continuous shrink (COUNT * (HOLD + SHRINK) = 75s) so existing pacing/tuning still holds.
    var SAFE_ZONE_INITIAL_RADIUS = BASE_SAFE_ZONE_INITIAL_RADIUS
        private set
    const val SAFE_ZONE_MIN_RADIUS = 1000f
    const val SAFE_ZONE_STAGE_COUNT = 3
    const val SAFE_ZONE_STAGE_HOLD_SECONDS = 7f
    var SAFE_ZONE_STAGE_SHRINK_SECONDS = BASE_SAFE_ZONE_STAGE_SHRINK_SECONDS
        private set

    // The zone used to freeze at SAFE_ZONE_MIN_RADIUS once the final round started, which
    // turned the finale into a static standoff decided almost entirely by whoever reached
    // the first power-up. Instead it keeps shrinking indefinitely from SAFE_ZONE_MIN_RADIUS
    // at a constant rate - the one that would reach SAFE_ZONE_FINAL_MIN_RADIUS after
    // SAFE_ZONE_FINAL_SHRINK_SECONDS - all the way down to SAFE_ZONE_ABSOLUTE_MIN_RADIUS,
    // rather than holding anywhere, so there's sustained pressure to keep moving and
    // fighting instead of camping a fixed circle, and a stalling match is eventually forced
    // to a decisive end.
    const val SAFE_ZONE_FINAL_MIN_RADIUS = 600f
    const val SAFE_ZONE_FINAL_SHRINK_SECONDS = 30f
    const val SAFE_ZONE_ABSOLUTE_MIN_RADIUS = 10f

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
    // bounded rate instead of snapping straight to the opposite heading. Slow enough that
    // a full reversal visibly takes about two seconds, not an instant flip.
    const val TURN_RATE_RADIANS_PER_SECOND = 1.6f

    fun applyArenaSize(size: ArenaSize) {
        WORLD_WIDTH = BASE_WORLD_WIDTH * size.scaleFactor
        WORLD_HEIGHT = BASE_WORLD_HEIGHT * size.scaleFactor
        SAFE_ZONE_INITIAL_RADIUS = BASE_SAFE_ZONE_INITIAL_RADIUS * size.scaleFactor
        SAFE_ZONE_STAGE_SHRINK_SECONDS = BASE_SAFE_ZONE_STAGE_SHRINK_SECONDS * size.scaleFactor
    }
}
