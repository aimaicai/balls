package com.hyperionsoftware.balls.economy

// How much Helium (see Wallet) each thing is worth. Kept as one place so match/achievement/
// daily-challenge payouts stay easy to rebalance together instead of scattered as magic
// numbers at each call site.
object HeliumRewards {
    private const val PER_ABSORPTION = 2
    private const val PER_TEN_SECONDS_SURVIVED = 1
    private const val WIN_BONUS = 20
    private const val FINAL_ROUND_BONUS = 10
    const val ACHIEVEMENT_UNLOCK_BONUS = 15
    const val DAILY_CHALLENGE_BONUS = 25

    // Rewards playing well over just playing long: absorbing and winning matter far more
    // than idling out the clock, but survival time still counts for something so a cautious,
    // still-alive-at-the-end match isn't worthless.
    fun forMatch(playerWon: Boolean, opponentsAbsorbed: Int, elapsedSeconds: Int, reachedFinalRound: Boolean): Int {
        var total = opponentsAbsorbed * PER_ABSORPTION + (elapsedSeconds / 10) * PER_TEN_SECONDS_SURVIVED
        if (playerWon) total += WIN_BONUS
        if (reachedFinalRound) total += FINAL_ROUND_BONUS
        return total
    }
}
