package io.github.caendeith.awakr.plugins.mission.code

import android.graphics.Bitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix

/** Decoding of still images and encoding of printable codes. */
internal object CodeImage {

    /** Reads one code out of a picture (gallery, saved shot, editor preview). */
    fun decodeResult(
        bitmap: Bitmap,
        reader: MultiFormatReader = CodeDecoder.reader(),
    ): Result? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return CodeDecoder.decodeResult(RGBLuminanceSource(width, height, pixels), reader)
    }

    /** Reads one code out of a luma plane (camera analysis frame). */
    fun decodeLuminance(
        data: ByteArray,
        width: Int,
        height: Int,
        reader: MultiFormatReader = CodeDecoder.reader(),
    ): Result? {
        if (width <= 0 || height <= 0 || data.size < width * height) return null
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        return CodeDecoder.decodeResult(source, reader)
    }

    /** Renders a printable code; null when the payload does not fit the symbology. */
    fun encode(text: String, format: CodeFormat, width: Int = 480, height: Int = 480): Bitmap? =
        matrix(text, format, width, height)?.let { toBitmap(it) }

    fun matrix(text: String, format: CodeFormat, width: Int, height: Int): BitMatrix? =
        CodeDecoder.write(text, format, width, height)

    fun toBitmap(matrix: BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
