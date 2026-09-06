package com.hyperionsoftware.balls.race

import com.hyperionsoftware.balls.game.Vector2

class RacePlayerBlob(
    id: Int,
    position: Vector2,
    color: Int
) : RaceBlob(id, position, RaceConfig.BASE_RADIUS, color) {

    @Volatile
    var inputDirection: Vector2 = Vector2(0f, 0f)

    override fun decideDirection(engine: RaceEngine, dt: Float): Vector2 = inputDirection
}
