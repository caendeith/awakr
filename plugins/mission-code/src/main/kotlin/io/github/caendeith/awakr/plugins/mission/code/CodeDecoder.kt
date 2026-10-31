package io.github.caendeith.awakr.plugins.mission.code

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer

/** Shared ZXing plumbing so the mission and the editor decode exactly the same way. */
internal object CodeDecoder {

    fun reader(): MultiFormatReader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to CodeFormat.readerFormats,
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    fun decodeResult(source: LuminanceSource, reader: MultiFormatReader = reader()): Result? =
        try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        } catch (_: ReaderException) {
            null
        } finally {
            reader.reset()
        }

    fun formatOf(result: Result): CodeFormat? =
        CodeFormat.all.firstOrNull { it.barcode == result.barcodeFormat }

    fun write(text: String, format: CodeFormat, width: Int, height: Int): BitMatrix? =
        try {
            MultiFormatWriter().encode(
                text,
                format.barcode,
                width,
                height,
                // Retail symbologies need a real quiet zone or the reader cannot find the guards.
                mapOf(EncodeHintType.MARGIN to if (format.numeric) 10 else 1),
            )
        } catch (_: WriterException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}
