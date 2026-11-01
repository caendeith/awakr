package io.github.caendeith.awakr.plugins.mission.code

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CodePayloadTest {

    private fun config(vararg pairs: Pair<String, String>): JsonObject =
        JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) })

    @Test
    fun readsPayloadAndFormatFromTheConfig() {
        val parsed = config(
            CodePayload.KEY_PAYLOAD to "AWAKR-X9TCMB63",
            CodePayload.KEY_FORMAT to "ean13",
        )
        assertEquals("AWAKR-X9TCMB63", CodePayload.payload(parsed))
        assertEquals(CodeFormat.EAN13, CodePayload.format(parsed))
        assertEquals("awakr-x9tcmb63", CodePayload.expected(parsed))
    }

    @Test
    fun emptyConfigMeansQrAndAnyCode() {
        val parsed = JsonObject(emptyMap())
        assertEquals("", CodePayload.expected(parsed))
        assertEquals(CodeFormat.QR, CodePayload.format(parsed))
        assertTrue(CodePayload.matches("", "anything at all"))
    }

    @Test
    fun unknownFormatFallsBackToQr() {
        assertEquals(CodeFormat.QR, CodeFormat.byId("pdf417"))
        assertEquals(CodeFormat.QR, CodeFormat.byId(null))
        assertEquals(CodeFormat.CODE128, CodeFormat.byId("CODE128"))
    }

    @Test
    fun generatedQrTokenMatchesTheTokenFormat() {
        val token = CodePayload.generate(CodeFormat.QR, Random(4))
        assertTrue(token.startsWith("AWAKR-"))
        assertEquals(14, token.length)
        assertTrue(token.drop(6).all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
        assertEquals(token, CodePayload.generate(CodeFormat.QR, Random(4)))
    }

    @Test
    fun generatedRetailCodesCarryAValidCheckDigit() {
        val random = Random(7)
        CodeFormat.all.filter { it.numeric }.forEach { format ->
            repeat(20) {
                val code = CodePayload.generate(format, random)
                assertEquals(format.length, code.length)
                assertTrue(code.all { it in '0'..'9' })
                assertEquals(code.last() - '0', CodePayload.checkDigit(code.dropLast(1)))
                assertTrue(CodePayload.isValid(code, format))
            }
        }
    }

    @Test
    fun knownRetailCodesAreValidated() {
        assertTrue(CodePayload.isValid("4006381333931", CodeFormat.EAN13))
        assertFalse(CodePayload.isValid("4006381333932", CodeFormat.EAN13))
        assertTrue(CodePayload.isValid("96385074", CodeFormat.EAN8))
        assertFalse(CodePayload.isValid("96385075", CodeFormat.EAN8))
        assertTrue(CodePayload.isValid("036000291452", CodeFormat.UPCA))
        assertFalse(CodePayload.isValid("036000291453", CodeFormat.UPCA))
        assertFalse(CodePayload.isValid("036000291452", CodeFormat.EAN13))
        assertFalse(CodePayload.isValid("0360002914ab", CodeFormat.UPCA))
    }

    @Test
    fun matchingIgnoresCaseAndSpaces() {
        assertTrue(CodePayload.matches("AWAKR-X9TCMB63", " awakr-x9tcmb63 "))
        assertFalse(CodePayload.matches("AWAKR-X9TCMB63", "AWAKR-AAAAAAAA"))
        assertTrue(CodePayload.matches("4012345678901", "4012345678901"))
        assertFalse(CodePayload.matches("4012345678901", "4012345678902"))
    }

    @Test
    fun retailCodesHaveToPassTheCheckDigitEvenWhenThePayloadIsEmpty() {
        assertTrue(CodePayload.accepts("", "4006381333931", CodeFormat.EAN13))
        assertFalse(CodePayload.accepts("", "4006381333932", CodeFormat.EAN13))
        assertTrue(CodePayload.accepts("", "AWAKR-X9TCMB63", CodeFormat.QR))
    }

    @Test
    fun aNullExpectationAcceptsAnyWellFormedCode() {
        assertTrue(CodePayload.accepts(null, "4006381333931", CodeFormat.EAN13))
        assertFalse(CodePayload.accepts(null, "4006381333932", CodeFormat.EAN13))
    }

    @Test
    fun aBoundProductCodeIsComparedAsTextNotAsAChecksum() {
        // Whatever the user scanned becomes the key: a code with a broken check digit still works.
        val scanned = "4006381333932"
        assertFalse(CodePayload.isValid(scanned, CodeFormat.EAN13))
        assertTrue(CodePayload.accepts(scanned, scanned, CodeFormat.EAN13))
    }

    @Test
    fun upcAScannedAsAThirteenDigitEanStillMatches() {
        // ZXing reports UPC-A as EAN-13 with a leading zero.
        assertTrue(CodePayload.accepts("036000291452", "0036000291452", CodeFormat.EAN13))
        assertTrue(CodePayload.matches("036000291452", "0036000291452"))
    }

    @Test
    fun differentCodesDoNotMatch() {
        assertFalse(CodePayload.accepts("036000291452", "036000291453", CodeFormat.UPCA))
        assertFalse(CodePayload.matches("000", "0000"))
    }

    @Test
    fun numericFormatsRejectNonDigits() {
        assertFalse(CodePayload.accepts("", "ABC", CodeFormat.EAN8))
        assertTrue(CodePayload.accepts("", "some text", CodeFormat.CODE128))
    }

    @Test
    fun switchingToARetailFormatReplacesAnUnencodablePayload() {
        val letters = "AWAKR-ARFKV857"
        val kept = CodePayload.ensureEncodable(letters, CodeFormat.EAN13)
        assertEquals(13, kept.length)
        assertTrue(CodePayload.isValid(kept, CodeFormat.EAN13))
        assertTrue(kept.all { it in '0'..'9' })
    }

    @Test
    fun switchingKeepsAPayloadTheNewFormatCanEncode() {
        val qr = CodePayload.generate(CodeFormat.QR)
        assertEquals(qr, CodePayload.ensureEncodable(qr, CodeFormat.CODE128))
        assertEquals("", CodePayload.ensureEncodable("", CodeFormat.EAN8))
    }
}
