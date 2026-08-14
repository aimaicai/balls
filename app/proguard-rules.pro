# Deliberately near-empty: the app has no reflection-based serialization (no Gson, no
# kotlinx.serialization, no Parcelable/Serializable data classes read back by field name),
# so there's nothing whose names R8 could break by renaming/removing at the default
# `proguard-android-optimize.txt` aggressiveness. Persistence is plain SharedPreferences
# keyed by hardcoded string constants (see the *Settings objects and HighScores), which
# aren't affected by obfuscation either way.
#
# AppCompat/Material/ConstraintLayout each ship their own consumer ProGuard rules bundled
# in their AARs and applied automatically - no need to duplicate anything for them here.
#
# If a real "works in debug, breaks in release" bug ever shows up (the classic symptom is a
# ClassNotFoundException/NoSuchMethodError only in the minified build), add the specific
# `-keep` rule that fixes it here, with a comment explaining why - not preemptively.
