package io.github.caendeith.awakr.ui.background

import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * On-disk helpers for the user's own background photos.
 *
 * Kept free of Android APIs so the naming, ordering and cleanup rules are unit-testable.
 */
object BackgroundFiles {
    const val DIR_NAME = "backgrounds"
    const val MAX_EDGE = 1600
    const val JPEG_QUALITY = 85
    const val FILE_PREFIX = "bg_"

    private val EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")
    private val NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val LABEL_TIME = DateTimeFormatter.ofPattern("HH:mm")

    /** Only the extensions the importer writes (plus the formats it can decode) are listed. */
    fun isSupportedName(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return false
        return name.substring(dot + 1).lowercase() in EXTENSIONS
    }

    /** Target file for an import: `bg_<timestamp>_<unique>.jpg`. */
    fun newFile(
        dir: File,
        timestampMs: Long,
        unique: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): File {
        val stamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), zone).format(NAME_TIME)
        return File(dir, FILE_PREFIX + stamp + "_" + unique + ".jpg")
    }

    /** "HH:mm" of the moment the photo was added, used as its name in the picker. */
    fun timeLabel(file: File, zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), zone).format(LABEL_TIME)

    /**
     * Newest first, skipping deleted files, empty files and anything that is not one of our images.
     * The list never contains the same path twice, so a double import cannot show up as two tiles.
     */
    fun list(dir: File): List<File> = dir.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it.length() > 0L && isSupportedName(it.name) }
        ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })
        ?.distinctBy { it.absolutePath }
        ?.toList()
        .orEmpty()

    /** Guards deletes so only files the app owns can be removed. */
    fun isInside(dir: File, file: File): Boolean = runCatching {
        file.canonicalFile.toPath().startsWith(dir.canonicalFile.toPath())
    }.getOrDefault(false)

    fun idOf(file: File): String = BackgroundIds.file(file.absolutePath)

    fun fileOf(id: String?): File? = BackgroundIds.filePath(id)?.let(::File)
}
