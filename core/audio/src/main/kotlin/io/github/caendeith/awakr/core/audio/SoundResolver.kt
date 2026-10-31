package io.github.caendeith.awakr.core.audio

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Turns the string stored in [io.github.caendeith.awakr.core.alarm.Alarm.soundId] into a playable URI.
 *
 * Formats:
 *  - `builtin:<id>` – one of [BuiltinSounds.all]
 *  - `file:<absolute path>` – a sound the user imported into the app's private storage
 *  - empty – the user's default sound
 *
 * Resolution never fails: an unknown or missing value falls back to the default sound and, if even
 * that is unavailable, to the first shipped tone, so an alarm always rings.
 */
object SoundResolver {

    fun uri(context: Context, soundId: String?, defaultSoundId: String? = null): Uri {
        val chosen = SoundChoice.resolve(soundId, defaultSoundId) { isPlayable(context, it) }
        return resolve(context, chosen) ?: builtinUri(context, BuiltinSounds.default())
    }

    private fun isPlayable(context: Context, soundId: String): Boolean = when {
        soundId.startsWith(BuiltinSounds.BUILTIN_PREFIX) ->
            BuiltinSounds.byId(soundId.removePrefix(BuiltinSounds.BUILTIN_PREFIX)) != null
        soundId.startsWith(BuiltinSounds.FILE_PREFIX) -> {
            val file = File(soundId.removePrefix(BuiltinSounds.FILE_PREFIX))
            file.isFile && file.length() > 0L
        }
        else -> false
    }

    /** Resolves without a fallback; null when the stored value points at nothing playable. */
    fun resolve(context: Context, soundId: String?): Uri? {
        if (soundId.isNullOrBlank()) return null
        return when {
            soundId.startsWith(BuiltinSounds.BUILTIN_PREFIX) -> {
                val sound = BuiltinSounds.byId(soundId.removePrefix(BuiltinSounds.BUILTIN_PREFIX))
                sound?.let { builtinUri(context, it) }
            }
            soundId.startsWith(BuiltinSounds.FILE_PREFIX) -> {
                val file = File(soundId.removePrefix(BuiltinSounds.FILE_PREFIX))
                if (file.isFile && file.length() > 0L) Uri.fromFile(file) else null
            }
            else -> null
        }
    }

    /** True when the value points at a shipped tone or an existing imported file. */
    fun exists(context: Context, soundId: String?): Boolean = resolve(context, soundId) != null

    /** Human readable name of the tone, or null when the value points at nothing. */
    fun name(context: Context, soundId: String?): String? {
        if (soundId.isNullOrBlank()) return null
        if (soundId.startsWith(BuiltinSounds.BUILTIN_PREFIX)) {
            val sound = BuiltinSounds.byId(soundId.removePrefix(BuiltinSounds.BUILTIN_PREFIX))
            return sound?.let { context.getString(it.nameRes) }
        }
        if (soundId.startsWith(BuiltinSounds.FILE_PREFIX)) {
            val file = File(soundId.removePrefix(BuiltinSounds.FILE_PREFIX))
            return if (file.isFile) file.nameWithoutExtension else null
        }
        return null
    }

    private fun builtinUri(context: Context, sound: BuiltinSound): Uri =
        Uri.parse("android.resource://" + context.packageName + "/" + sound.rawRes)
}
