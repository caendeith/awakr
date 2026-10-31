package io.github.caendeith.awakr.plugins.mission.code

import com.google.zxing.common.BitMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CodeImageTest {

    private fun luminance(matrix: BitMatrix): ByteArray {
        val out = ByteArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                out[y * matrix.width + x] = if (matrix.get(x, y)) 0 else 255.toByte()
            }
        }
        return out
    }

    private fun decode(matrix: BitMatrix): String? =
        CodeImage.decodeLuminance(luminance(matrix), matrix.width, matrix.height)?.text

    @Test
    fun qrRoundTrip() {
        val payload = "AWAKR-X9TCMB63"
        val matrix = CodeImage.matrix(payload, CodeFormat.QR, 240, 240)
        assertNotNull(matrix)
        assertEquals(payload, decode(matrix!!))
    }

    @Test
    fun retailBarcodesRoundTrip() {
        listOf(
            CodePayload.generate(CodeFormat.EAN13) to CodeFormat.EAN13,
            CodePayload.generate(CodeFormat.EAN8) to CodeFormat.EAN8,
            CodePayload.generate(CodeFormat.UPCA) to CodeFormat.UPCA,
            "AWAKR-X9TCMB63" to CodeFormat.CODE128,
        ).forEach { (payload, format) ->
            val matrix = CodeImage.matrix(payload, format, 600, 200)
            assertNotNull("matrix for $payload", matrix)
            assertEquals("decode of $format", payload, decode(matrix!!))
        }
    }

    @Test
    fun rotatedQrStillDecodes() {
        val payload = "AWAKR-ROTATE01"
        val matrix = CodeImage.matrix(payload, CodeFormat.QR, 240, 240)!!
        val raw = luminance(matrix)
        (90 to 270).let { (first, second) ->
            listOf(first, 180, second).forEach { degrees ->
                val (data, width, height) = CodeFrame.rotate(raw, matrix.width, matrix.height, degrees)
                assertEquals(payload, CodeImage.decodeLuminance(data, width, height)?.text)
            }
        }
    }

    @Test
    fun garbageFrameDecodesToNull() {
        val data = ByteArray(64 * 64) { 128.toByte() }
        assertNull(CodeImage.decodeLuminance(data, 64, 64))
        assertFalse(data.isEmpty())
    }

    @Test
    fun oversizedOrEmptyInputIsRejected() {
        assertNull(CodeImage.decodeLuminance(ByteArray(10), 640, 480))
        assertNull(CodeImage.decodeLuminance(ByteArray(0), 0, 0))
    }
}
