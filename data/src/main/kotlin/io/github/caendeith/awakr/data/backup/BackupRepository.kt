package io.github.caendeith.awakr.data.backup

import io.github.caendeith.awakr.data.db.dao.AlarmDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Reads and writes backup files for the alarm list and the app preferences.
 *
 * Streams, not Uri: the caller (the app module) owns the Storage Access Framework contract, this
 * class stays Android-Context-free and therefore unit-testable.
 */
class BackupRepository(
    private val dao: AlarmDao,
    private val settings: SettingsBackupStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Writes the current alarms and preferences as JSON and closes [out]. */
    suspend fun exportTo(out: OutputStream): ExportSummary = withContext(ioDispatcher) {
        val alarms = dao.getAll()
        val payload = BackupPayload(
            exportedAt = clock(),
            alarms = alarms,
            settings = settings.snapshot(),
        )
        out.use { stream ->
            stream.write(BackupCodec.encode(payload).toByteArray(Charsets.UTF_8))
            stream.flush()
        }
        ExportSummary(alarmCount = alarms.size, settingsIncluded = payload.settings != null)
    }

    /** Parses a backup without touching the database, so the UI can ask for a mode first. */
    suspend fun readFrom(input: InputStream): BackupDecodeResult = withContext(ioDispatcher) {
        val text = try {
            input.use { it.readCapped(MAX_BACKUP_BYTES) }
        } catch (e: IOException) {
            return@withContext BackupDecodeResult.Failure(
                BackupError.Malformed(e.message ?: "the file could not be read"),
            )
        }
        BackupCodec.decode(text)
    }

    /**
     * Applies a decoded backup.
     *
     * Alarms always arrive with a fresh row id: ids from another install are meaningless here and
     * could collide with rows left over from an earlier import.
     */
    suspend fun importInto(
        payload: BackupPayload,
        mode: ImportMode,
        skippedAlarms: Int = 0,
    ): ImportSummary = withContext(ioDispatcher) {
        val now = clock()
        val rows = payload.alarms.map { alarm ->
            alarm.copy(
                id = 0L,
                deletedAt = null,
                // A skip that already happened means nothing on the importing device.
                skipNextAt = alarm.skipNextAt?.takeIf { it > now },
                createdAt = alarm.createdAt.takeIf { it > 0L } ?: now,
                updatedAt = now,
            )
        }

        if (mode == ImportMode.REPLACE) {
            dao.purgeDeleted()
            dao.getAll().forEach { dao.deleteById(it.id) }
        }

        var order = if (mode == ImportMode.REPLACE) 0 else dao.nextSortOrder()
        rows.forEach { alarm ->
            dao.insert(alarm.copy(sortOrder = order))
            order++
        }

        val settingsApplied = payload.settings?.let { settings.restore(it); true } ?: false
        ImportSummary(
            mode = mode,
            imported = rows.size,
            skipped = skippedAlarms,
            settingsApplied = settingsApplied,
        )
    }

    private companion object {
        /** Guards against picking a huge file by mistake. */
        const val MAX_BACKUP_BYTES = 8 * 1024 * 1024
    }
}

/** Reads the whole stream as UTF-8 text, refusing files above [limit] bytes. */
private fun InputStream.readCapped(limit: Int): String {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(chunk)
        if (read < 0) break
        buffer.write(chunk, 0, read)
        if (buffer.size() > limit) throw IOException("the file is larger than ${limit / (1024 * 1024)} MB")
    }
    return buffer.toByteArray().toString(Charsets.UTF_8)
}
