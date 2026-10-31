package io.github.caendeith.awakr.plugins.mission.code

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Where a picture taken while solving the mission ends up: always a private copy under
 * `files/shots`, plus a copy in the system gallery on API 29+ where no storage permission is
 * needed. The private copy keeps the feature working on older devices too.
 */
internal object CodeStorage {

    private const val TAG = "AwakrCode"
    private const val FOLDER = "Awakr"

    /** Saves the captured JPEG privately and, when possible, into Pictures/Awakr. */
    fun saveShot(context: Context, jpeg: ByteArray): Uri? {
        val file = runCatching {
            val dir = File(context.filesDir, "shots").apply { mkdirs() }
            File(dir, "awakr-" + System.currentTimeMillis() + ".jpg").apply { writeBytes(jpeg) }
        }.onFailure { Log.w(TAG, "could not save the picture", it) }.getOrNull() ?: return null
        return insertMedia(context, file.name, "image/jpeg", jpeg) ?: Uri.fromFile(file)
    }

    /** Renders a printable PNG into the gallery so it can be shared or opened from another app. */
    fun savePng(context: Context, bitmap: Bitmap, name: String = "awakr-code.png"): Uri? {
        val bytes = ByteArrayOutputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return null
            out.toByteArray()
        }
        return insertMedia(context, name, "image/png", bytes)
    }

    fun shareIntent(uri: Uri, mime: String, title: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_TITLE, title)
        }

    private fun insertMedia(context: Context, displayName: String, mime: String, bytes: ByteArray): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            val target = uri
            resolver.openOutputStream(target)?.use { it.write(bytes) } ?: return null
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(target, values, null, null)
            target
        }.onFailure { Log.w(TAG, "could not add $displayName to the gallery", it) }.getOrNull()
    }

    fun compress(bitmap: Bitmap): ByteArray? = runCatching {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.toByteArray()
        }
    }.getOrNull()
}
