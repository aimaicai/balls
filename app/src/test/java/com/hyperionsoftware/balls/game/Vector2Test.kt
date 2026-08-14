package com.hyperionsoftware.balls.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Vector2Test {

    @Test
    fun `length matches Pythagorean distance from origin`() {
        assertEquals(5f, Vector2(3f, 4f).length(), 0.0001f)
    }

    @Test
    fun `normalized preserves direction and has unit length`() {
        val normalized = Vector2(3f, 4f).normalized()
        assertEquals(1f, normalized.length(), 0.0001f)
        assertEquals(0.6f, normalized.x, 0.0001f)
        assertEquals(0.8f, normalized.y, 0.0001f)
    }

    @Test
    fun `normalizing a near-zero vector returns zero instead of dividing by near-zero`() {
        val normalized = Vector2(0.00001f, 0f).normalized()
        assertEquals(0f, normalized.x, 0.0001f)
        assertEquals(0f, normalized.y, 0.0001f)
    }

    @Test
    fun `distanceTo is symmetric and matches the length of the difference`() {
        val a = Vector2(1f, 1f)
        val b = Vector2(4f, 5f)
        assertEquals(5f, a.distanceTo(b), 0.0001f)
        assertEquals(a.distanceTo(b), b.distanceTo(a), 0.0001f)
    }

    @Test
    fun `dot product of perpendicular vectors is zero`() {
        assertEquals(0f, Vector2(1f, 0f).dot(Vector2(0f, 1f)), 0.0001f)
    }

    @Test
    fun `dot product of a unit vector with itself is one`() {
        assertEquals(1f, Vector2(1f, 0f).dot(Vector2(1f, 0f)), 0.0001f)
    }

    @Test
    fun `plus and minus are inverses`() {
        val a = Vector2(2f, 3f)
        val b = Vector2(5f, -1f)
        val result = (a + b) - b
        assertEquals(a.x, result.x, 0.0001f)
        assertEquals(a.y, result.y, 0.0001f)
    }

    @Test
    fun `times scales both components`() {
        val scaled = Vector2(2f, -3f) * 2.5f
        assertEquals(5f, scaled.x, 0.0001f)
        assertEquals(-7.5f, scaled.y, 0.0001f)
    }

    @Test
    fun `zero vector has zero length and is its own normalized form`() {
        val zero = Vector2(0f, 0f)
        assertTrue(zero.length() == 0f)
        assertEquals(0f, zero.normalized().length(), 0.0001f)
    }
}
