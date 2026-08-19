package com.hyperionsoftware.balls.settings

import android.content.Context
import com.hyperionsoftware.balls.cosmetics.BalloonSticker
import com.hyperionsoftware.balls.cosmetics.PlayerColor

// Which balloon color and sticker the player has chosen, persisted the same way as the other
// settings objects. Defaults to SKY_BLUE/NONE, the original unadorned look, so nobody's
// balloon changes on update until they actually visit the customize screen and pick something.
object CosmeticsSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_PLAYER_COLOR = "player_color"
    private const val KEY_STICKER = "sticker"

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

    fun getSelectedSticker(context: Context): BalloonSticker {
        val storedName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STICKER, null)
        return BalloonSticker.entries.find { it.name == storedName } ?: BalloonSticker.NONE
    }

    fun setSelectedSticker(context: Context, sticker: BalloonSticker) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STICKER, sticker.name)
            .apply()
    }
}
