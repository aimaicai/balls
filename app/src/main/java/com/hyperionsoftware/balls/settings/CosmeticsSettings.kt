package com.hyperionsoftware.balls.settings

import android.content.Context
import com.hyperionsoftware.balls.cosmetics.BalloonCord
import com.hyperionsoftware.balls.cosmetics.BalloonSticker
import com.hyperionsoftware.balls.cosmetics.ExhaustStyle
import com.hyperionsoftware.balls.cosmetics.PlayerColor

// Which balloon color, sticker, cord and exhaust style the player has chosen, persisted the
// same way as the other settings objects. Defaults to SKY_BLUE/NONE/CLASSIC_GREY/CLASSIC, the
// original unadorned look, so nobody's balloon changes on update until they actually visit
// the customize screen and pick something.
object CosmeticsSettings {
    private const val PREFS_NAME = "settings"
    private const val KEY_PLAYER_COLOR = "player_color"
    private const val KEY_STICKER = "sticker"
    private const val KEY_CORD = "cord"
    private const val KEY_EXHAUST_STYLE = "exhaust_style"

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

    fun getSelectedCord(context: Context): BalloonCord {
        val storedName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CORD, null)
        return BalloonCord.entries.find { it.name == storedName } ?: BalloonCord.CLASSIC_GREY
    }

    fun setSelectedCord(context: Context, cord: BalloonCord) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CORD, cord.name)
            .apply()
    }

    fun getSelectedExhaustStyle(context: Context): ExhaustStyle {
        val storedName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EXHAUST_STYLE, null)
        return ExhaustStyle.entries.find { it.name == storedName } ?: ExhaustStyle.CLASSIC
    }

    fun setSelectedExhaustStyle(context: Context, style: ExhaustStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EXHAUST_STYLE, style.name)
            .apply()
    }
}
