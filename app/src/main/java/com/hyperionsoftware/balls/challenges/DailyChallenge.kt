package com.hyperionsoftware.balls.challenges

import com.hyperionsoftware.balls.R

// What a single completed match looked like - exactly the data GameActivity.onGameOver
// already receives, so evaluating a challenge never needs any new tracking wired through
// the engine itself.
data class MatchResult(
    val playerWon: Boolean,
    val finalRadius: Int,
    val opponentsAbsorbed: Int,
    val elapsedSeconds: Int,
    val reachedFinalRound: Boolean
)

// One a day (see DailyChallenges), same for every player since there's no backend to hand
// out a real shared one - each just needs to be checkable from a single match's own result.
enum class DailyChallenge(val descriptionResId: Int) {
    WIN_A_MATCH(R.string.challenge_win_a_match) {
        override fun isSatisfiedBy(result: MatchResult) = result.playerWon
    },
    ABSORB_FIVE(R.string.challenge_absorb_five) {
        override fun isSatisfiedBy(result: MatchResult) = result.opponentsAbsorbed >= 5
    },
    ABSORB_TEN(R.string.challenge_absorb_ten) {
        override fun isSatisfiedBy(result: MatchResult) = result.opponentsAbsorbed >= 10
    },
    REACH_FINAL_ROUND(R.string.challenge_reach_final_round) {
        override fun isSatisfiedBy(result: MatchResult) = result.reachedFinalRound
    },
    WIN_FROM_FINAL_ROUND(R.string.challenge_win_from_final_round) {
        override fun isSatisfiedBy(result: MatchResult) = result.playerWon && result.reachedFinalRound
    },
    SURVIVE_THREE_MINUTES(R.string.challenge_survive_three_minutes) {
        override fun isSatisfiedBy(result: MatchResult) = result.elapsedSeconds >= 180
    },
    REACH_SIZE_200(R.string.challenge_reach_size_200) {
        override fun isSatisfiedBy(result: MatchResult) = result.finalRadius >= 200
    },
    QUICK_WIN(R.string.challenge_quick_win) {
        override fun isSatisfiedBy(result: MatchResult) = result.playerWon && !result.reachedFinalRound
    };

    abstract fun isSatisfiedBy(result: MatchResult): Boolean
}
