package com.hyperionsoftware.balls.game

// A small, repeatable cast of behavioral archetypes for bots - like Pac-Man's four ghosts,
// there are usually more bots in a match than personalities, so the same handful of
// "characters" show up more than once instead of every bot behaving identically or getting a
// fully random, one-off temperament.
//
// A bot's personality is decided by its balloon COLOR (see PALETTE below), not by raw spawn
// order - deliberately, so a color always means the same character and a player can learn to
// recognize "the red ones hunt, the green ones hoard power-ups" etc. across matches (see the
// legend in TutorialActivity). This is meant as a playtesting aid while personalities are
// still being tuned; if it turns out to give away too much once the game ships, only PALETTE
// and the legend section need to go, nothing else about BotBlob/GameEngine depends on it.
//
// BALANCED sits first on purpose: every multiplier below is neutral (1x), reproducing the
// original, personality-less BotBlob behavior exactly. Existing tests build bots via
// GameEngine without choosing a personality and always inspect whichever bot spawned first,
// which this keeps pinned to BALANCED - nothing about their expected behavior changes.
enum class BotPersonality(
    // Scales visionRadius as a whole - how far this personality notices threats, prey and
    // power-ups in the first place.
    val visionMultiplier: Float,
    // Scales the "already basically about to collide" panic-flee distance - bigger reacts to
    // a closing threat a little sooner (flees more cautiously), smaller is willing to let it
    // get closer before bailing.
    val fleeBufferMultiplier: Float,
    // Scales how early (as a fraction of the current safe-zone radius) this personality
    // proactively heads back toward the zone center before actually being caught outside it.
    val zoneMarginMultiplier: Float,
    // How willing this personality is to break off chasing visible prey for a nearby
    // power-up instead: the detour is taken once the power-up is closer than the prey by
    // this fraction (0.6 means "only if it's within 60% of the prey's own distance"). A
    // permanent stat pickup (SPEED_UP/AGILITY_UP/POTENCY_UP) gets an extra bonus on top of
    // this (see BotBlob.PERMANENT_UPGRADE_DETOUR_BONUS) since its benefit outlasts the single
    // pickup, unlike everything else.
    val pickupDetourThreshold: Float
) {
    BALANCED(visionMultiplier = 1f, fleeBufferMultiplier = 1f, zoneMarginMultiplier = 1f, pickupDetourThreshold = 0.6f),

    // Bold and single-minded: barely detours for a pickup mid-chase, holds its nerve a beat
    // longer before fleeing, and hunts with a wider vision.
    HUNTER(visionMultiplier = 1.15f, fleeBufferMultiplier = 0.85f, zoneMarginMultiplier = 1.1f, pickupDetourThreshold = 0.3f),

    // Shy: flees threats from further away and heads back to the safe zone earlier, trading
    // hunting opportunities for survival margin.
    CAUTIOUS(visionMultiplier = 0.9f, fleeBufferMultiplier = 1.3f, zoneMarginMultiplier = 0.85f, pickupDetourThreshold = 0.6f),

    // Greedy for upgrades: readily breaks off a chase for a nearby power-up, especially a
    // permanent stat one, even when it's a bit further away than the prey it was chasing.
    COLLECTOR(visionMultiplier = 1f, fleeBufferMultiplier = 1f, zoneMarginMultiplier = 1f, pickupDetourThreshold = 1.1f);

    companion object {
        // The single source of truth for both a bot's color AND its personality - GameEngine
        // assigns bots color-then-personality straight from this list (cycling through it by
        // spawn order the same way the old flat color array did), and the same list drives
        // the color legend in TutorialActivity, so the two can never drift apart. Two colors
        // per personality since there are twice as many colors as personalities; every
        // original color value is kept, just paired up with a personality instead of handed
        // out by raw index.
        val PALETTE: List<Pair<Int, BotPersonality>> = listOf(
            0xFFEF5350.toInt() to HUNTER, // red
            0xFF66BB6A.toInt() to COLLECTOR, // green
            0xFFAB47BC.toInt() to CAUTIOUS, // purple
            0xFF26C6DA.toInt() to BALANCED, // cyan
            0xFFFFA726.toInt() to HUNTER, // orange
            0xFF9CCC65.toInt() to COLLECTOR, // light green
            0xFF5C6BC0.toInt() to CAUTIOUS, // indigo
            0xFFEC407A.toInt() to BALANCED // pink
        )

        // Every color assigned to this personality, in PALETTE order - what the legend shows
        // next to each personality's name/description.
        fun colorsFor(personality: BotPersonality): List<Int> =
            PALETTE.filter { it.second == personality }.map { it.first }
    }
}
