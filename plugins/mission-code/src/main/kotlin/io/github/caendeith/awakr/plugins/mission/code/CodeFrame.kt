package io.github.caendeith.awakr.plugins.mission.code

/**
 * Turns a CameraX analysis frame into a ZXing luminance source.
 *
 * CameraX hands over a YUV_420_888 buffer whose luma plane can be padded, and the sensor may be
 * mounted rotated, so both the stride and the rotation have to be handled before decoding.
 */
internal object CodeFrame {

    /** Copies the luma plane row by row, dropping the row padding. */
    fun stripRowPadding(raw: ByteArray, rowStride: Int, width: Int, height: Int): ByteArray {
        if (rowStride == width) return raw
        val expected = rowStride * height
        if (raw.size < expected) return raw
        val out = ByteArray(width * height)
        for (row in 0 until height) {
            raw.copyInto(out, destinationOffset = row * width, startIndex = row * rowStride, endIndex = row * rowStride + width)
        }
        return out
    }

    /** Rotates a luma plane clockwise; dimensions travel with it. */
    fun rotate(data: ByteArray, width: Int, height: Int, degrees: Int): Triple<ByteArray, Int, Int> {
        val normalised = ((degrees % 360) + 360) % 360
        if (normalised == 0 || data.size < width * height) return Triple(data, width, height)
        return when (normalised) {
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
