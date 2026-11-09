package io.github.caendeith.awakr.ui.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.caendeith.awakr.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Owns the background photos the user adds.
 *
 * Files live in the app's private folder, so they need no permission and disappear with an
 * uninstall. Imports are downscaled on the way in: a 12 MP camera shot would otherwise cost tens of
 * megabytes of bitmap for a full screen image.
 */
object BackgroundStore {
    fun dir(context: Context): File = File(context.filesDir, BackgroundFiles.DIR_NAME).apply { mkdirs() }

    fun photos(context: Context): List<File> = BackgroundFiles.list(dir(context))

    /** True when [ref] can actually be drawn right now. */
    fun isUsable(ref: BackgroundRef): Boolean = when (ref) {
        is BackgroundRef.Builtin -> BackgroundCatalog.byKey(ref.key) != null
        is BackgroundRef.Local -> File(ref.path).let { it.isFile && it.length() > 0L }
    }

    /** Resolves alarm id -> app setting -> null (the plain ring screen). */
    fun resolve(context: Context, alarmId: String?, settingsId: String?): String? =
        BackgroundResolver.resolve(alarmId, settingsId, ::isUsable)

    /** Human readable name of an id, or null when it cannot be resolved. */
    fun name(context: Context, id: String?): String? = when (val ref = BackgroundIds.parse(id)) {
        is BackgroundRef.Builtin -> BackgroundCatalog.byKey(ref.key)?.let { context.getString(it.nameRes) }
        is BackgroundRef.Local -> context.getString(
            R.string.bg_photo_name,
            BackgroundFiles.timeLabel(File(ref.path)),
        )

        null -> null
    }

    /** Deletes one of our photos; anything outside the app folder is refused. */
    fun delete(context: Context, id: String?): Boolean {
        val file = BackgroundFiles.fileOf(id) ?: return false
        if (!BackgroundFiles.isInside(dir(context), file)) return false
        return runCatching { file.delete() }.getOrDefault(false)
    }

    /**
     * Copies [uri] into the private folder as a JPEG whose long edge is at most [BackgroundFiles.MAX_EDGE].
     * Returns the new background id, or null when the image cannot be read.
     */
    suspend fun import(
        context: Context,
        uri: Uri,
        timestampMs: Long = System.currentTimeMillis(),
    ): String? = withContext(Dispatchers.IO) {
        // The content uri is streamed to a temp file first: bounds, pixels and EXIF all need a
        // second read, and not every provider allows reopening the stream.
        val temp = runCatching { File.createTempFile("bg_import", ".tmp", context.cacheDir) }.getOrNull()
            ?: return@withContext null
        try {
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (!copied) return@withContext null

            val bitmap = readBitmap(temp, BackgroundFiles.MAX_EDGE) ?: return@withContext null
            val target = BackgroundFiles.newFile(
                dir(context),
                timestampMs,
                UUID.randomUUID().toString().take(8),
            )
            val saved = runCatching {
                FileOutputStream(target).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, BackgroundFiles.JPEG_QUALITY, output)
                }
            }.getOrDefault(false)
            if (!saved) {
                target.delete()
                return@withContext null
            }
            BackgroundFiles.idOf(target)
        } finally {
            temp.delete()
        }
    }

    /** Decodes a photo of ours for drawing, downscaled to [maxEdge] on the long edge. */
    suspend fun load(
        path: String,
        maxEdge: Int = BackgroundFiles.MAX_EDGE,
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        readBitmap(File(path), maxEdge)?.asImageBitmap()
    }

    private fun readBitmap(file: File, maxEdge: Int): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        // A corrupt or huge file must never crash the ring screen.
        val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }
            .getOrNull() ?: return null
        return scaleDown(applyOrientation(file, decoded), maxEdge)
    }

    /** Power-of-two downscale, so the decoder never allocates the full size in memory. */
    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxEdge) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /** Camera photos carry their rotation in EXIF; without this a portrait shot lies on its side. */
    private fun applyOrientation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(bitmap, width, height, true) }
            .getOrDefault(bitmap)
    }
}
