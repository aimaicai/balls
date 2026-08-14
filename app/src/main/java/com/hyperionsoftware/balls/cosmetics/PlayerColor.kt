package com.hyperionsoftware.balls.cosmetics

import android.graphics.Color
import com.hyperionsoftware.balls.R
import com.hyperionsoftware.balls.achievements.Achievement

// Purely cosmetic - which color the player's OWN balloon is drawn in. Bots always keep
// their own fixed palette (see GameEngine's bot colors), so this never affects them. A few
// are free from the start; the rest unlock through achievements already worth chasing for
// their own sake, so picking a favorite becomes another small reward for playing instead of
// a separate grind. Chosen to stay visually distinct from the bot palette, the power-up
// colors, and the safe-zone/danger-indicator reds so the player's own balloon never gets
// confused for any of those.
enum class PlayerColor(val colorInt: Int, val labelResId: Int, val requiredAchievement: Achievement?) {
    SKY_BLUE(Color.parseColor("#4FC3F7"), R.string.color_sky_blue, null), // the original, unchanged default
    ROSE(Color.parseColor("#F06292"), R.string.color_rose, null),
    SEAFOAM(Color.parseColor("#4DB6AC"), R.string.color_seafoam, null),
    SUNSET(Color.parseColor("#FFA000"), R.string.color_sunset, Achievement.FIRST_WIN),
    LAVENDER(Color.parseColor("#9575CD"), R.string.color_lavender, Achievement.FINAL_ROUND),
    CRIMSON(Color.parseColor("#C62828"), R.string.color_crimson, Achievement.ABSORB_STREAK),
    EMERALD(Color.parseColor("#2E7D32"), R.string.color_emerald, Achievement.MAX_SPEED_STAT),
    MIDNIGHT(Color.parseColor("#303F9F"), R.string.color_midnight, Achievement.MAX_POTENCY_STAT)
}
