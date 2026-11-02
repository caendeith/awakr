package io.github.caendeith.awakr.core.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The app's private folder for user sounds (`filesDir/sounds`) and the operations the picker needs.
 *
 * Both imported files and recordings live here, so the picker can list them as one "Recorded" pack
 * and [SoundResolver] can play them through the same `file:<path>` id.
 */
object SoundStorage {

    private const val TAG = "SoundStorage"

    fun dir(context: Context): File =
        File(context.filesDir, SoundFiles.DIR_NAME).apply { if (!isDirectory) mkdirs() }

    /** User sounds, newest first. Safe to call from any thread. */
    fun recorded(context: Context): List<RecordedSound> = SoundFiles.listIn(dir(context))

    /**
     * Deletes one user sound. Only files inside our own folder are touched.
     *
     * @return true when the file is gone afterwards
     */
    fun delete(context: Context, soundId: String?): Boolean {
        val file = SoundFiles.fileOf(soundId) ?: return false
        if (!SoundFiles.isInside(dir(context), file)) {
            Log.w(TAG, "refusing to delete outside the sound folder")
            return false
        }
        return runCatching { !file.exists() || file.delete() }.getOrDefault(false)
    }

    /**
     * Copies a document the user picked into private storage and returns its `file:<path>` id.
     *
     * The copy is what makes the sound survive the document picker's temporary grant and device
     * reboots, exactly like the old "My own file" flow did.
     */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val extension = context.contentResolver.getType(uri)
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() && it.length <= 5 }
                ?: "bin"
            val target = File(dir(context), UUID.randomUUID().toString() + "." + extension)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.isFile && target.length() > 0L) {
                SoundFiles.idOf(target)
            } else {
                target.delete()
                null
            }
        }.onFailure { Log.w(TAG, "cannot import sound", it) }.getOrNull()
    }
}
