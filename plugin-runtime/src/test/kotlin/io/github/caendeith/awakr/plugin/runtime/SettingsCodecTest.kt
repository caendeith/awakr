package io.github.caendeith.awakr.plugin.runtime

import io.github.caendeith.awakr.sdk.SettingField
import io.github.caendeith.awakr.sdk.StringRes
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    private val fields = listOf(
        SettingField.Toggle("vibrate", StringRes(0, "Vibrate"), default = true),
        SettingField.Slider("count", StringRes(0, "Count"), min = 1f, max = 5f, default = 3f),
        SettingField.TextInput("name", StringRes(0, "Name"), default = "abc"),
    )

    @Test
    fun defaultsComeFromFields() {
        val values = SettingsCodec.defaults(fields)
        assertEquals("true", values["vibrate"].toString())
        assertEquals("3.0", values["count"].toString())
        assertEquals("\"abc\"", values["name"].toString())
    }

    @Test
    fun parseInvalidJsonFallsBackToEmpty() {
        assertTrue(SettingsCodec.parse("not json").isEmpty())
        assertTrue(SettingsCodec.parse(null).isEmpty())
    }

    @Test
    fun mergeKeepsStoredValues() {
        val stored = SettingsCodec.parse("""{"vibrate":false}""")
        val merged = SettingsCodec.merge(fields, stored)
        assertFalse(SettingsCodec.boolean(merged, "vibrate", true))
        assertEquals(3f, SettingsCodec.float(merged, "count", 1f))
        assertEquals("abc", SettingsCodec.string(merged, "name", ""))
    }

    @Test
    fun roundTripEncodeParse() {
        val values = SettingsCodec.defaults(fields)
        val copy = SettingsCodec.parse(SettingsCodec.encode(values))
        assertEquals(JsonPrimitive("abc"), copy["name"])
    }
}
