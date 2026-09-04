package com.hyperionsoftware.balls.game

// A small, repeatable cast of behavioral archetypes for bots - like Pac-Man's four ghosts,
// there are usually more bots in a match than personalities, so the same handful of
// "characters" show up more than once instead of every bot behaving identically or getting a
// fully random, one-off temperament. Assigned cyclically by spawn order (see GameEngine).
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
    COLLECTOR(visionMultiplier = 1f, fleeBufferMultiplier = 1f, zoneMarginMultiplier = 1f, pickupDetourThreshold = 1.1f)
}
