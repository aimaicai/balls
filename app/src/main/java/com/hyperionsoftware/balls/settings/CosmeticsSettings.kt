package com.hyperionsoftware.balls.settings

import android.content.Context
import com.hyperionsoftware.balls.cosmetics.PlayerColor

// Which balloon color the player has chosen, persisted the same way as the other settings
// objects. Defaults to SKY_BLUE, the original hardcoded color, so nobody's balloon changes
// on update until they actually visit the customize screen and pick something else.
object CosmeticsSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_PLAYER_COLOR = "player_color"

    fun getSelectedColor(context: Context): PlayerColor {
        val storedName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PLAYER_COLOR, null)
        return PlayerColor.entries.find { it.name == storedName } ?: PlayerColor.SKY_BLUE
    }

    fun setSelectedColor(context: Context, color: PlayerColor) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLAYER_COLOR, color.name)
            .apply()
    }
}
