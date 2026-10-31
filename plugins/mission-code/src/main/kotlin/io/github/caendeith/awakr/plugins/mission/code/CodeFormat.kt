package io.github.caendeith.awakr.plugins.mission.code

import com.google.zxing.BarcodeFormat

/**
 * Codes a mission accepts.
 *
 * Only the formats people actually meet on packaging are listed: QR plus the four most common
 * retail barcodes. Anything else is rejected at the reader level so a random barcode on a cereal
 * box cannot dismiss the alarm by accident.
 */
enum class CodeFormat(
    val id: String,
    val barcode: BarcodeFormat,
    val nameRes: Int,
    /** Fixed-length digit-only symbology with a check digit (EAN/UPC). */
    val numeric: Boolean = false,
    val length: Int = 0,
) {
    QR("qr", BarcodeFormat.QR_CODE, R.string.code_format_qr),
    EAN13("ean13", BarcodeFormat.EAN_13, R.string.code_format_ean13, numeric = true, length = 13),
    EAN8("ean8", BarcodeFormat.EAN_8, R.string.code_format_ean8, numeric = true, length = 8),
    UPCA("upca", BarcodeFormat.UPC_A, R.string.code_format_upca, numeric = true, length = 12),
    CODE128("code128", BarcodeFormat.CODE_128, R.string.code_format_code128),
    ;

    companion object {
        val all: List<CodeFormat> = entries.toList()

        fun byId(id: String?): CodeFormat =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: QR

        /** Formats ZXing is asked about while scanning. */
        val readerFormats: List<BarcodeFormat> = all.map { it.barcode }
    }
}
