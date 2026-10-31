package io.github.caendeith.awakr.plugins.mission.qr

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QrPayloadTest {

    @Test
    fun readsThePayloadFromTheConfig() {
        val config = JsonObject(mapOf(QrPayload.KEY_PAYLOAD to JsonPrimitive(" AWAKR-ABCD2345 ")))
        assertEquals("AWAKR-ABCD2345", QrPayload.expected(config))
    }

    @Test
    fun treatsMissingPayloadAsEmpty() {
        assertEquals("", QrPayload.expected(JsonObject(emptyMap())))
    }

    @Test
    fun generatesReadableCodes() {
        val code = QrPayload.generate(Random(7))
        assertTrue(code.startsWith("AWAKR-"))
        assertEquals("AWAKR-".length + 8, code.length)
        assertFalse(code.drop("AWAKR-".length).any { it == '0' || it == 'O' || it == '1' || it == 'I' })
    }

    @Test
    fun generatedCodesAreStableForTheSameSeed() {
        assertEquals(QrPayload.generate(Random(42)), QrPayload.generate(Random(42)))
    }

    @Test
    fun matchesIgnoresCaseAndOuterSpaces() {
        assertTrue(QrPayload.matches("AWAKR-ABCD2345", " awakr-abcd2345 "))
        assertFalse(QrPayload.matches("AWAKR-ABCD2345", "AWAKR-ABCD2346"))
    }

    @Test
    fun blankPayloadAcceptsAnyCode() {
        assertTrue(QrPayload.matches("", "anything at all"))
        assertTrue(QrPayload.matches("   ", "anything at all"))
    }
}
