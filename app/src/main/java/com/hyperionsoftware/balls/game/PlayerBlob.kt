package com.hyperionsoftware.balls.game

class PlayerBlob(
    id: Int,
    position: Vector2,
    color: Int
) : Blob(id, position, GameConfig.BASE_RADIUS, color) {

    @Volatile
    var inputDirection: Vector2 = Vector2(0f, 0f)

    override fun decideDirection(engine: GameEngine, dt: Float): Vector2 = inputDirection
}
