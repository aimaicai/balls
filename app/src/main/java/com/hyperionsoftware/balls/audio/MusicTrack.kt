package com.hyperionsoftware.balls.audio

import com.hyperionsoftware.balls.R

// A growing playlist of real, bundled MP3s (see res/raw) rather than the old procedurally
// synthesized loops - each entry pairs a display label with the raw resource it plays.
// Starts with just one track; meant to be extended with more entries as they're added,
// nothing else about the selection/playback code needs to change when that happens.
enum class MusicTrack(val labelResId: Int, val rawResId: Int) {
    CIELI_DI_ZUCCHERO(R.string.music_track_cieli_di_zucchero, R.raw.cieli_di_zucchero)
}
