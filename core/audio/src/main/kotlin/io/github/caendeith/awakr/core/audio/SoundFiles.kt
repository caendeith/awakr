package io.github.caendeith.awakr.core.audio

import java.io.File

/**
 * A sound the user owns: either imported from their storage or recorded in the app.
 *
 * Both arrive through the same `file:<absolute path>` format, so the alarm, the ring service and
 * [SoundResolver] do not care which one it is.
 */
data class RecordedSound(val file: File) {

    val soundId: String = BuiltinSounds.FILE_PREFIX + file.absolutePath

    /** "20:41" for a clip recorded in the app; null for an imported file. */
    val timeLabel: String? = RecordingRules.timeLabel(file.name)

    /** File name without its extension, used for imported files. */
    val displayName: String = file.nameWithoutExtension

    val isRecording: Boolean = timeLabel != null
}

/**
 * Pure helpers for the `file:<absolute path>` storage format and the folder that holds user
 * sounds. No Android calls, so the listing and parsing rules are unit tested on the JVM.
 */
object SoundFiles {

    /** Folder inside the app's private files directory. */
    const val DIR_NAME: String = "sounds"

    private val SUPPORTED_EXTENSIONS: Set<String> = setOf(
        "m4a", "mp4", "aac", "mp3", "wav", "ogg", "flac", "opus", "amr", "3gp",
    )

    fun idOf(file: File): String = BuiltinSounds.FILE_PREFIX + file.absolutePath

    /** The file behind a `file:<path>` id, or null for any other format. */
    fun fileOf(soundId: String?): File? {
        val path = soundId
            ?.takeIf { it.startsWith(BuiltinSounds.FILE_PREFIX) }
            ?.removePrefix(BuiltinSounds.FILE_PREFIX)
            ?.trim()
        if (path.isNullOrEmpty()) return null
        return File(path)
    }

    fun isSupportedName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS

    /** Playable user sounds in [dir], newest first; empty files and unknown formats are skipped. */
    fun listIn(dir: File): List<RecordedSound> {
        val files = dir.listFiles() ?: return emptyList()
        return files.asSequence()
            .filter { it.isFile && it.length() > 0L && isSupportedName(it.name) }
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .map(::RecordedSound)
            .toList()
    }

    /** True when [file] sits directly inside [dir]; checked before deleting anything. */
    fun isInside(dir: File, file: File): Boolean = runCatching {
        file.canonicalFile.parentFile?.canonicalPath == dir.canonicalFile.canonicalPath
    }.getOrDefault(false)
}
