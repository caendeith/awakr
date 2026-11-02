package io.github.caendeith.awakr.data.backup

import io.github.caendeith.awakr.data.db.dao.AlarmDao
import io.github.caendeith.awakr.data.db.entity.AlarmEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class BackupRepositoryTest {

    private companion object {
        const val NOW = 1_800_000_000_000L
    }

    private class FakeAlarmDao : AlarmDao {
        val rows = mutableListOf<AlarmEntity>()
        private var nextId = 1L
        private val flow = MutableStateFlow<List<AlarmEntity>>(emptyList())

        override fun observeAll(): Flow<List<AlarmEntity>> = flow

        override suspend fun getAll(): List<AlarmEntity> = visible()

        private fun visible(): List<AlarmEntity> =
            rows.filter { it.deletedAt == null }
                .sortedWith(compareBy({ it.sortOrder }, { it.hour }, { it.minute }))

        override suspend fun getById(id: Long): AlarmEntity? = rows.firstOrNull { it.id == id }

        override suspend fun getEnabled(): List<AlarmEntity> =
            rows.filter { it.enabled && it.deletedAt == null }

        override suspend fun nextSortOrder(): Int = (rows.maxOfOrNull { it.sortOrder } ?: -1) + 1

        override suspend fun insert(alarm: AlarmEntity): Long {
            val id = if (alarm.id == 0L) nextId++ else alarm.id
            rows.removeAll { it.id == id }
            rows.add(alarm.copy(id = id))
            flow.value = visible()
            return id
        }

        override suspend fun update(alarm: AlarmEntity) {
            insert(alarm)
        }

        override suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long) {
            updateField(id) { it.copy(enabled = enabled, updatedAt = updatedAt) }
        }

        override suspend fun setSortOrder(id: Long, order: Int, updatedAt: Long) {
            updateField(id) { it.copy(sortOrder = order, updatedAt = updatedAt) }
        }

        override suspend fun setSkipNextAt(id: Long, skipAt: Long?, updatedAt: Long) {
            updateField(id) { it.copy(skipNextAt = skipAt, updatedAt = updatedAt) }
        }

        override suspend fun softDelete(id: Long, deletedAt: Long) {
            updateField(id) { it.copy(deletedAt = deletedAt, updatedAt = deletedAt) }
        }

        override suspend fun restore(id: Long, updatedAt: Long) {
            updateField(id) { it.copy(deletedAt = null, updatedAt = updatedAt) }
        }

        override suspend fun purgeDeleted() {
            rows.removeAll { it.deletedAt != null }
            flow.value = visible()
        }

        override suspend fun deleteById(id: Long) {
            rows.removeAll { it.id == id }
            flow.value = visible()
        }

        private fun updateField(id: Long, change: (AlarmEntity) -> AlarmEntity) {
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0) rows[index] = change(rows[index])
            flow.value = visible()
        }
    }

    private class FakeSettingsStore(initial: BackupSettings = BackupSettings()) : SettingsBackupStore {
        var current = initial
        var restores = 0
        override suspend fun snapshot(): BackupSettings = current
        override suspend fun restore(settings: BackupSettings) {
            current = settings
            restores++
        }
    }

    private fun repository(
        dao: FakeAlarmDao,
        store: FakeSettingsStore,
    ) = BackupRepository(
        dao = dao,
        settings = store,
        clock = { NOW },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun alarm(hour: Int, minute: Int, sortOrder: Int = 0, label: String = "") = AlarmEntity(
        hour = hour,
        minute = minute,
        sortOrder = sortOrder,
        label = label,
    )

    @Test
    fun `export writes a file that can be read back`() = runBlocking {
        val dao = FakeAlarmDao()
        dao.insert(alarm(6, 30, sortOrder = 0, label = "Morning"))
        val store = FakeSettingsStore(BackupSettings(themeMode = "DARK", defaultSnoozeMinutes = 20))
        val repository = repository(dao, store)

        val out = ByteArrayOutputStream()
        val summary = repository.exportTo(out)

        assertEquals(1, summary.alarmCount)
        assertTrue(summary.settingsIncluded)
        val decoded = repository.readFrom(ByteArrayInputStream(out.toByteArray()))
        val success = decoded as BackupDecodeResult.Success
        assertEquals(1, success.payload.alarms.size)
        assertEquals("Morning", success.payload.alarms.single().label)
        assertEquals(NOW, success.payload.exportedAt)
        assertEquals("DARK", success.payload.settings?.themeMode)
        assertEquals(20, success.payload.settings?.defaultSnoozeMinutes)
    }

    @Test
    fun `replace mode deletes existing alarms and imports in file order`() = runBlocking {
        val dao = FakeAlarmDao()
        val old = dao.insert(alarm(5, 0, sortOrder = 0, label = "Old"))
        val store = FakeSettingsStore()

        val payload = BackupPayload(
            exportedAt = 0L,
            alarms = listOf(
                alarm(7, 15, sortOrder = 5, label = "First"),
                alarm(8, 45, sortOrder = 9, label = "Second"),
            ),
            settings = BackupSettings(defaultSnoozeMinutes = 25),
        )

        val summary = repository(dao, store).importInto(payload, ImportMode.REPLACE)

        assertEquals(ImportMode.REPLACE, summary.mode)
        assertEquals(2, summary.imported)
        assertTrue(summary.settingsApplied)
        assertEquals(listOf("First", "Second"), dao.getAll().map { it.label })
        assertEquals(listOf(0, 1), dao.getAll().map { it.sortOrder })
        assertNull(dao.getById(old))
        assertEquals(25, store.current.defaultSnoozeMinutes)
        assertEquals(1, store.restores)
    }

    @Test
    fun `merge mode keeps existing alarms and appends imported ones`() = runBlocking {
        val dao = FakeAlarmDao()
        dao.insert(alarm(5, 0, sortOrder = 0, label = "Keep me"))

        val payload = BackupPayload(
            exportedAt = 0L,
            alarms = listOf(alarm(9, 10, sortOrder = 0, label = "Added")),
            settings = null,
        )

        val summary = repository(dao, FakeSettingsStore()).importInto(payload, ImportMode.MERGE)

        assertEquals(ImportMode.MERGE, summary.mode)
        assertEquals(1, summary.imported)
        assertTrue(!summary.settingsApplied)
        val rows = dao.getAll()
        assertEquals(listOf("Keep me", "Added"), rows.map { it.label })
        assertEquals(listOf(0, 1), rows.map { it.sortOrder })
    }

    @Test
    fun `imported rows get fresh ids and timestamps`() = runBlocking {
        val dao = FakeAlarmDao()
        val payload = BackupPayload(
            exportedAt = 0L,
            alarms = listOf(
                alarm(6, 0).copy(
                    id = 42L,
                    createdAt = 1_600_000_000_000L,
                    updatedAt = 1_600_000_000_000L,
                    skipNextAt = NOW - 1,
                ),
            ),
            settings = null,
        )

        repository(dao, FakeSettingsStore()).importInto(payload, ImportMode.REPLACE)

        val imported = dao.getAll().single()
        assertEquals(1L, imported.id)
        assertEquals(1_600_000_000_000L, imported.createdAt)
        assertEquals(NOW, imported.updatedAt)
        // A skip that already fired on the old device is dropped, not carried over.
        assertNull(imported.skipNextAt)
    }

    @Test
    fun `future skip stays after import`() = runBlocking {
        val dao = FakeAlarmDao()
        val payload = BackupPayload(
            exportedAt = 0L,
            alarms = listOf(alarm(6, 0).copy(skipNextAt = NOW + 60_000L)),
            settings = null,
        )

        repository(dao, FakeSettingsStore()).importInto(payload, ImportMode.REPLACE)

        assertEquals(NOW + 60_000L, dao.getAll().single().skipNextAt)
    }

    @Test
    fun `unreadable file is reported as malformed instead of throwing`() = runBlocking {
        val dao = FakeAlarmDao()
        val repository = repository(dao, FakeSettingsStore())

        val broken = repository.readFrom(ByteArrayInputStream("{oops".toByteArray()))
        assertTrue((broken as BackupDecodeResult.Failure).error is BackupError.Malformed)

        val failing = object : java.io.InputStream() {
            override fun read(): Int = throw IOException("disk is gone")
        }
        val unreadable = repository.readFrom(failing)
        assertTrue((unreadable as BackupDecodeResult.Failure).error is BackupError.Malformed)
    }

    @Test
    fun `skipped alarm count is passed through to the summary`() = runBlocking {
        val dao = FakeAlarmDao()
        val payload = BackupPayload(exportedAt = 0L, alarms = listOf(alarm(6, 0)), settings = null)

        val summary = repository(dao, FakeSettingsStore()).importInto(payload, ImportMode.REPLACE, 3)

        assertEquals(3, summary.skipped)
    }
}
