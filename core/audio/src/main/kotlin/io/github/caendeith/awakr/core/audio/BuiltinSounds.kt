package io.github.caendeith.awakr.core.audio

/** One of the tones that ship inside the app. */
data class BuiltinSound(
    val id: String,
    val nameRes: Int,
    val rawRes: Int,
)

/**
 * The built-in alarm tones.
 *
 * Every tone is a short, loopable WAV generated for this project (no third party assets),
 * so the app stays free of licensing questions and the release build stays small.
 */
object BuiltinSounds {

    const val DEFAULT_ID: String = "chime"

    /** Prefix used in [io.github.caendeith.awakr.core.alarm.Alarm.soundId] for a shipped tone. */
    const val BUILTIN_PREFIX: String = "builtin:"

    /** Prefix used for a tone the user imported from their own storage. */
    const val FILE_PREFIX: String = "file:"

    val all: List<BuiltinSound> = listOf(
        BuiltinSound("chime", R.string.sound_chime_name, R.raw.sound_chime),
        BuiltinSound("marimba", R.string.sound_marimba_name, R.raw.sound_marimba),
        BuiltinSound("bell", R.string.sound_bell_name, R.raw.sound_bell),
        BuiltinSound("digital", R.string.sound_digital_name, R.raw.sound_digital),
        BuiltinSound("sunrise", R.string.sound_sunrise_name, R.raw.sound_sunrise),
        BuiltinSound("alarm", R.string.sound_alarm_name, R.raw.sound_alarm),
        BuiltinSound("pulse", R.string.sound_pulse_name, R.raw.sound_pulse),
    )

    fun byId(id: String?): BuiltinSound? = all.firstOrNull { it.id == id }

    fun default(): BuiltinSound = byId(DEFAULT_ID) ?: all.first()

    /** `builtin:chime` for the given tone. */
    fun idOf(sound: BuiltinSound): String = BUILTIN_PREFIX + sound.id
}
