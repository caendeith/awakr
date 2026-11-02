package io.github.caendeith.awakr.data.backup

import io.github.caendeith.awakr.data.db.entity.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun weekdaysAlarm(id: Long = 3L) = AlarmEntity(
        id = id,
        hour = 7,
        minute = 30,
        daysMask = 0b0011111,
        enabled = true,
        label = "Wake up \"now\"",
        soundUri = "content://media/42",
        soundPackId = "pack-1",
        soundId = "file:/storage/emulated/0/a.mp3",
        rampSeconds = 45,
        volumeStart = 40,
        vibrate = false,
        snoozeEnabled = true,
        snoozeMinutes = 5,
        maxSnoozes = 2,
        missionPluginId = "math",
        missionConfig = """{"difficulty":"hard","rounds":3}""",
        sortOrder = 0,
        skipNextAt = 1_800_000_000_000L,
        deletedAt = null,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_500L,
    )

    private val oneShotAlarm = AlarmEntity(
        id = 9L,
        hour = 22,
        minute = 5,
        daysMask = 0,
        enabled = false,
        label = "",
        sortOrder = 1,
    )

    private val settings = BackupSettings(
        themeMode = "DARK",
        swipeAction = "TOGGLE",
        defaultSnoozeMinutes = 15,
        defaultMissionPluginId = "shake",
        defaultSoundId = "builtin:bell",
    )

    @Test
    fun `round trip keeps every alarm and settings field`() {
        val payload = BackupPayload(
            exportedAt = 1_762_000_000_000L,
            alarms = listOf(weekdaysAlarm(), oneShotAlarm),
            settings = settings,
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(payload))

        assertTrue(decoded is BackupDecodeResult.Success)
        val success = decoded as BackupDecodeResult.Success
        assertEquals(0, success.skippedAlarms)
        assertEquals(1_762_000_000_000L, success.payload.exportedAt)
        assertEquals(listOf(weekdaysAlarm(), oneShotAlarm), success.payload.alarms)
        assertEquals(settings, success.payload.settings)
    }

    @Test
    fun `file advertises the app and schema`() {
        val text = BackupCodec.encode(
            BackupPayload(exportedAt = 0L, alarms = emptyList(), settings = null),
        )
        val root = JsonParser.parse(text) as JsonValue.Obj
        assertEquals("awakr", root.string("app"))
        assertEquals(1L, root.long("schema"))
    }

    @Test
    fun `unknown fields are ignored at every level`() {
        val text = """
            {
              "schema": 1,
              "app": "awakr",
              "exportedAt": 5,
              "futureRootField": {"nested": [1, 2, 3]},
              "alarms": [
                {"hour": 6, "minute": 15, "futureField": true, "daysMask": 64}
              ],
              "settings": {"themeMode": "dark", "unknownSetting": "x"}
            }
        """.trimIndent()

        val success = BackupCodec.decode(text) as BackupDecodeResult.Success
        assertEquals(1, success.payload.alarms.size)
        val alarm = success.payload.alarms.single()
        assertEquals(6, alarm.hour)
        assertEquals(15, alarm.minute)
        // Out-of-range mask bits are dropped instead of producing an invalid repeat.
        assertEquals(64, alarm.daysMask)
        assertEquals("DARK", success.payload.settings?.themeMode)
        assertEquals(BackupSettings().defaultSoundId, success.payload.settings?.defaultSoundId)
    }

    @Test
    fun `missing alarm fields fall back to entity defaults`() {
        val text = """{"schema":1,"app":"awakr","alarms":[{"hour":6,"minute":15}]}"""
        val alarm = (BackupCodec.decode(text) as BackupDecodeResult.Success).payload.alarms.single()
        assertEquals(AlarmEntity(hour = 6, minute = 15), alarm)
        assertNull((BackupCodec.decode(text) as BackupDecodeResult.Success).payload.settings)
    }

    @Test
    fun `newer schema is rejected`() {
        val text = """{"schema":2,"app":"awakr","alarms":[]}"""
        val failure = BackupCodec.decode(text) as BackupDecodeResult.Failure
        assertEquals(BackupError.UnsupportedSchema(2), failure.error)
    }

    @Test
    fun `foreign app is rejected`() {
        val text = """{"schema":1,"app":"alarmy","alarms":[]}"""
        val failure = BackupCodec.decode(text) as BackupDecodeResult.Failure
        assertEquals(BackupError.NotAwakrBackup("alarmy"), failure.error)
    }

    @Test
    fun `broken file is reported as malformed`() {
        assertTrue(BackupCodec.decode("not json") is BackupDecodeResult.Failure)
        assertTrue(BackupCodec.decode("[]") is BackupDecodeResult.Failure)
        assertTrue(BackupCodec.decode("""{"app":"awakr"}""") is BackupDecodeResult.Failure)
        val wrongShape = BackupCodec.decode("""{"schema":1,"app":"awakr","alarms":{"hour":7}}""")
        assertTrue((wrongShape as BackupDecodeResult.Failure).error is BackupError.Malformed)
    }

    @Test
    fun `unusable alarm rows are counted as skipped`() {
        val text = """
            {
              "schema": 1,
              "app": "awakr",
              "alarms": [
                {"hour": 6, "minute": 15},
                {"hour": 25, "minute": 0},
                {"minute": 30},
                {"hour": 8, "minute": 45, "deletedAt": 1700000000000},
                "garbage"
              ]
            }
        """.trimIndent()

        val success = BackupCodec.decode(text) as BackupDecodeResult.Success
        assertEquals(1, success.payload.alarms.size)
        assertEquals(4, success.skippedAlarms)
    }

    @Test
    fun `newer preferences are exported and read back`() {
        val settings = BackupSettings(
            dynamicColor = true,
            preAlertMinutes = 10,
            language = "ru",
        )
        val payload = BackupPayload(exportedAt = 7L, alarms = emptyList(), settings = settings)

        val text = BackupCodec.encode(payload)
        val root = JsonParser.parse(text) as JsonValue.Obj
        val raw = root.obj("settings")!!
        assertEquals(true, raw.bool("dynamicColor"))
        assertEquals(10, raw.int("preAlertMinutes"))
        assertEquals("ru", raw.string("language"))

        val decoded = (BackupCodec.decode(text) as BackupDecodeResult.Success).payload.settings
        assertEquals(settings, decoded)
    }

    @Test
    fun `older file without the newer preferences imports as absent`() {
        val text = """
            {
              "schema": 1,
              "app": "awakr",
              "alarms": [{"hour": 7, "minute": 0}],
              "settings": {"themeMode": "DARK", "defaultSnoozeMinutes": 15}
            }
        """.trimIndent()

        val settings = (BackupCodec.decode(text) as BackupDecodeResult.Success).payload.settings!!
        assertEquals("DARK", settings.themeMode)
        assertEquals(15, settings.defaultSnoozeMinutes)
        // Absent means "leave the device value alone", not "reset to the default".
        assertNull(settings.dynamicColor)
        assertNull(settings.preAlertMinutes)
        assertNull(settings.language)
    }

    @Test
    fun `unknown or oddly cased language is normalised`() {
        fun languageOf(raw: String): String? = (
            BackupCodec.decode(
                """{"schema":1,"app":"awakr","alarms":[],"settings":{"language":"$raw"}}""",
            ) as BackupDecodeResult.Success
            ).payload.settings?.language

        assertEquals("en", languageOf("en"))
        assertEquals("ru", languageOf("RU"))
        assertEquals("system", languageOf("de"))
        assertEquals("system", languageOf(""))
    }

    @Test
    fun `pre-alert and dynamic color are sanitised`() {
        fun settingsOf(raw: String): BackupSettings = (
            BackupCodec.decode(
                """{"schema":1,"app":"awakr","alarms":[],"settings":$raw}""",
            ) as BackupDecodeResult.Success
            ).payload.settings!!

        assertEquals(0, settingsOf("""{"preAlertMinutes":-5}""").preAlertMinutes)
        assertEquals(180, settingsOf("""{"preAlertMinutes":600}""").preAlertMinutes)
        // A wrong JSON type reads as absent instead of failing the whole import.
        assertNull(settingsOf("""{"dynamicColor":"yes","preAlertMinutes":"early"}""").dynamicColor)
        assertNull(settingsOf("""{"dynamicColor":"yes"}""").preAlertMinutes)
    }
}
