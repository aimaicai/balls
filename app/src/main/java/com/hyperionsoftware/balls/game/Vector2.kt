package com.hyperionsoftware.balls.game

import kotlin.math.sqrt

data class Vector2(var x: Float = 0f, var y: Float = 0f) {

    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vector2(x * scalar, y * scalar)

    fun length(): Float = sqrt(x * x + y * y)

    fun normalized(): Vector2 {
        val len = length()
        return if (len < 0.0001f) Vector2(0f, 0f) else Vector2(x / len, y / len)
    }

    // Deliberately not "(this - other).length()" - that allocates a throwaway Vector2 just to
    // discard it a line later. This is the single most-called method in the whole game (every
    // bot's per-tick perception scan, every collision check, called roughly once per PAIR of
    // blobs, so O(botCount^2) times per frame) - avoiding that allocation here removes the
    // single biggest source of GC churn in a match with a lot of bots.
    fun distanceTo(other: Vector2): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    fun dot(other: Vector2): Float = x * other.x + y * other.y
}
