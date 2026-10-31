package io.github.caendeith.awakr.plugins.mission.qr

/**
 * Pure byte-level frame helpers used by the camera analyser.
 *
 * They never touch [android.graphics.Bitmap] or the camera stack, so the whole
 * decoding path can be unit tested without a device.
 */
internal object QrFrame {

    /**
     * Copies one YUV plane into a tightly packed luminance array.
     *
     * [android.media.Image.Plane.rowStride] is usually wider than the image, so a
     * plain copy would skew every row of the frame.
     */
    fun stripRowPadding(raw: ByteArray, rowStride: Int, width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height)
        for (row in 0 until height) {
            val from = row * rowStride
            if (from >= raw.size) break
            System.arraycopy(raw, from, out, row * width, minOf(width, raw.size - from))
        }
        return out
    }

    /** ZXing expects an upright image, while the analyser always receives the sensor orientation. */
    fun rotate(data: ByteArray, width: Int, height: Int, degrees: Int): Triple<ByteArray, Int, Int> {
        return when (degrees % 360) {
            90 -> {
                val out = ByteArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[x * height + (height - 1 - y)] = data[y * width + x]
                    }
                }
                Triple(out, height, width)
            }

            180 -> {
                val out = ByteArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[(height - 1 - y) * width + (width - 1 - x)] = data[y * width + x]
                    }
                }
                Triple(out, width, height)
            }

            270 -> {
                val out = ByteArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[(width - 1 - x) * height + y] = data[y * width + x]
                    }
                }
                Triple(out, height, width)
            }

            else -> Triple(data, width, height)
        }
    }
}
