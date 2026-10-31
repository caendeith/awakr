package io.github.caendeith.awakr.plugins.mission.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises the same code path the camera analyser uses: pack a frame into a
 * luminance plane, rotate it to the upright orientation and let ZXing decode it.
 */
class QrFrameTest {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            ),
        )
    }

    @Test
    fun decodesUprightFrame() {
        val (data, width, height) = qrFrame(PAYLOAD)
        assertEquals(PAYLOAD, decode(data, width, height))
    }

    @Test
    fun decodesFrameThatTheSensorRotated() {
        val (upright, width, height) = qrFrame(PAYLOAD)
        for (degrees in listOf(90, 180, 270)) {
            val sensor = QrFrame.rotate(upright, width, height, (360 - degrees) % 360)
            val fixed = QrFrame.rotate(sensor.first, sensor.second, sensor.third, degrees)
            assertEquals("rotation ${degrees}°", PAYLOAD, decode(fixed.first, fixed.second, fixed.third))
        }
    }

    @Test
    fun rotateKeepsDimensionsForQuarterTurns() {
        val data = ByteArray(4 * 2) { it.toByte() }
        val quarter = QrFrame.rotate(data, 4, 2, 90)
        assertEquals(2, quarter.second)
        assertEquals(4, quarter.third)
        val back = QrFrame.rotate(quarter.first, quarter.second, quarter.third, 270)
        assertEquals(4, back.second)
        assertEquals(2, back.third)
        assertEquals(data.toList(), back.first.toList())
    }

    @Test
    fun rotateByZeroIsANoOp() {
        val data = ByteArray(6) { (it * 3).toByte() }
        val result = QrFrame.rotate(data, 3, 2, 0)
        assertEquals(3, result.second)
        assertEquals(2, result.third)
        assertEquals(data.toList(), result.first.toList())
    }

    @Test
    fun stripRowPaddingDropsStrideTail() {
        val width = 3
        val height = 2
        val stride = 5
        val raw = byteArrayOf(
            1, 2, 3, 99, 99,
            4, 5, 6, 99, 99,
        )
        val packed = QrFrame.stripRowPadding(raw, stride, width, height)
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5, 6), packed.toList())
    }

    @Test
    fun stripRowPaddingSurvivesTruncatedFrame() {
        val packed = QrFrame.stripRowPadding(byteArrayOf(7, 8), rowStride = 8, width = 2, height = 4)
        assertEquals(8, packed.size)
        assertEquals(7.toByte(), packed[0])
        assertEquals(8.toByte(), packed[1])
        assertEquals(0.toByte(), packed[2])
    }

    private fun decode(data: ByteArray, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        reader.reset()
        return try {
            reader.decodeWithState(bitmap)?.text
        } catch (_: com.google.zxing.ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }

    /** Renders [text] as a QR code and converts it to a YUV luminance plane. */
    private fun qrFrame(text: String, size: Int = 240): Triple<ByteArray, Int, Int> {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val data = ByteArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                data[y * size + x] = if (matrix.get(x, y)) 0 else 255.toByte()
            }
        }
        return Triple(data, size, size)
    }

    private companion object {
        const val PAYLOAD = "awakr-qr-mission"
    }
}
