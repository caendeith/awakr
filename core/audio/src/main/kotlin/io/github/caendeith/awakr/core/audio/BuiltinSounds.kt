package io.github.caendeith.awakr.core.audio

/**
 * A group of shipped tones.
 *
 * Packs are presentation only: the picker shows one section per pack, while an alarm still stores a
 * single `soundId`, so nothing changes in the database.
 */
enum class SoundPack {
    /** Soft, melodic tones that fade in gently. */
    GENTLE,

    /** Sharp, insistent tones for heavy sleepers. */
    LOUD,
}

/** One of the tones that ship inside the app. */
data class BuiltinSound(
    val id: String,
    val nameRes: Int,
    val rawRes: Int,
    val pack: SoundPack,
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

    /** Prefix used for a tone the user imported from their own storage or recorded. */
    const val FILE_PREFIX: String = "file:"

    val all: List<BuiltinSound> = listOf(
        BuiltinSound("chime", R.string.sound_chime_name, R.raw.sound_chime, SoundPack.GENTLE),
        BuiltinSound("marimba", R.string.sound_marimba_name, R.raw.sound_marimba, SoundPack.GENTLE),
        BuiltinSound("sunrise", R.string.sound_sunrise_name, R.raw.sound_sunrise, SoundPack.GENTLE),
        BuiltinSound("bell", R.string.sound_bell_name, R.raw.sound_bell, SoundPack.GENTLE),
        BuiltinSound("digital", R.string.sound_digital_name, R.raw.sound_digital, SoundPack.LOUD),
        BuiltinSound("alarm", R.string.sound_alarm_name, R.raw.sound_alarm, SoundPack.LOUD),
        BuiltinSound("pulse", R.string.sound_pulse_name, R.raw.sound_pulse, SoundPack.LOUD),
    )

    /** Packs in the order the picker shows them. */
    val packs: List<SoundPack> = SoundPack.entries

    fun byId(id: String?): BuiltinSound? = all.firstOrNull { it.id == id }

    /** Tones of one pack, in declaration order. */
    fun inPack(pack: SoundPack): List<BuiltinSound> = all.filter { it.pack == pack }

    fun default(): BuiltinSound = byId(DEFAULT_ID) ?: all.first()

    /** `builtin:chime` for the given tone. */
    fun idOf(sound: BuiltinSound): String = BUILTIN_PREFIX + sound.id

    /** True when [soundId] is `builtin:<id>` and points at a shipped tone. */
    fun isBuiltin(soundId: String?): Boolean = byId(idWithoutPrefix(soundId)) != null

    /** The bare id of `builtin:<id>`, or null for any other format. */
    fun idWithoutPrefix(soundId: String?): String? =
        soundId?.takeIf { it.startsWith(BUILTIN_PREFIX) }?.removePrefix(BUILTIN_PREFIX)
}
