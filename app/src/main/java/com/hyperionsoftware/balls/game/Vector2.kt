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

    fun distanceTo(other: Vector2): Float = (this - other).length()
}
