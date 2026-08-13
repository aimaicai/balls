package com.hyperionsoftware.balls.audio

import com.hyperionsoftware.balls.R

// Four distinct synthesized styles (see BackgroundMusicPlayer) rather than one fixed track,
// so players who don't want driving EDM have somewhere else to go.
enum class MusicTrack(val labelResId: Int) {
    ARCADE(R.string.music_track_arcade),
    CHILL(R.string.music_track_chill),
    CHIPTUNE(R.string.music_track_chiptune),
    ROCK(R.string.music_track_rock)
}
