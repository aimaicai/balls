package com.hyperionsoftware.balls.game

class PlayerBlob(
    id: Int,
    position: Vector2,
    color: Int
) : Blob(id, position, GameConfig.BASE_RADIUS, color) {

    @Volatile
    var inputDirection: Vector2 = Vector2(0f, 0f)

    override fun decideDirection(engine: GameEngine, dt: Float): Vector2 = inputDirection

    // The joystick only aims: it never moves the player by itself. Holding sprint is the
    // only thrust input, so it doubles as "am I actually accelerating right now".
    override fun wantsToAccelerate(hasHeading: Boolean): Boolean = isBoosting
}
