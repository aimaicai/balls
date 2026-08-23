package com.hyperionsoftware.balls.cosmetics

import android.graphics.Color
import com.hyperionsoftware.balls.R
import com.hyperionsoftware.balls.achievements.Achievement

// Which color the player's own dangling string (see GameView.drawString) is drawn in - purely
// cosmetic, same unlock pattern as PlayerColor/BalloonSticker (a couple free from the start,
// the rest behind achievements already worth chasing). Bots always keep the original plain
// grey string, this only ever affects the player's own balloon.
enum class BalloonCord(val colorInt: Int, val labelResId: Int, val requiredAchievement: Achievement?) {
    CLASSIC_GREY(Color.parseColor("#CFD8DC"), R.string.cord_classic_grey, null), // the original, unchanged default
    MIDNIGHT_BLACK(Color.parseColor("#37474F"), R.string.cord_midnight_black, null),
    GOLDEN(Color.parseColor("#FFD54F"), R.string.cord_golden, Achievement.FIRST_ABSORB),
    CRIMSON(Color.parseColor("#E53935"), R.string.cord_crimson, Achievement.SUPPLY_DROP),
    EMERALD(Color.parseColor("#43A047"), R.string.cord_emerald, Achievement.MAX_AGILITY_STAT),
    ELECTRIC_PURPLE(Color.parseColor("#AB47BC"), R.string.cord_electric_purple, Achievement.USE_HOOK)
}
