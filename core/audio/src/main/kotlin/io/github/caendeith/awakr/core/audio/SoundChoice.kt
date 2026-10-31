package io.github.caendeith.awakr.core.audio

/**
 * Pure part of [SoundResolver]: which stored value should actually be played.
 *
 * Kept free of Android so the fallback chain (alarm → default → shipped tone) can be unit tested
 * without a device.
 */
object SoundChoice {

    val FALLBACK: String = BuiltinSounds.BUILTIN_PREFIX + BuiltinSounds.DEFAULT_ID

    /**
     * @param requested value stored on the alarm, may be empty
     * @param default value from settings, may be empty or null
     * @param playable tells whether a value points at a shipped tone or an existing file
     */
    fun resolve(
        requested: String?,
        default: String?,
        playable: (String) -> Boolean,
    ): String {
        if (!requested.isNullOrBlank() && playable(requested)) return requested
        if (!default.isNullOrBlank() && default != requested && playable(default)) return default
        return FALLBACK
    }

    /** True when the value means "whatever the user picked in settings". */
    fun isInherited(requested: String?): Boolean = requested.isNullOrBlank()
}
