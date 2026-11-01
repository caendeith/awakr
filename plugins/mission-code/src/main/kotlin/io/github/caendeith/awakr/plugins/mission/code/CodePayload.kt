package io.github.caendeith.awakr.plugins.mission.code

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

/**
 * Expected payload of the code mission.
 *
 * An empty payload accepts any supported code (the "just scan something" mode). A generated code
 * is a printable token: QR and CODE-128 carry an `AWAKR-XXXXXXXX` body, retail formats carry a
 * valid check digit so any scanner app can read them back.
 */
internal object CodePayload {

    const val KEY_PAYLOAD = "payload"
    const val KEY_FORMAT = "format"

    private const val PREFIX = "AWAKR-"
    private const val BODY_LENGTH = 8
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun payload(config: JsonObject): String =
        (config[KEY_PAYLOAD] as? JsonPrimitive)?.content?.trim().orEmpty()

    fun format(config: JsonObject): CodeFormat =
        CodeFormat.byId((config[KEY_FORMAT] as? JsonPrimitive)?.content)

    /** Payload the camera has to read, normalised for comparison. */
    fun expected(config: JsonObject): String = normalize(payload(config))

    fun generate(format: CodeFormat, random: Random = Random.Default): String =
        when (format) {
            CodeFormat.QR -> token(random)
            CodeFormat.CODE128 -> token(random)
            CodeFormat.EAN13 -> withCheckDigit(("20" + digits(10, random)).take(12))
            CodeFormat.EAN8 -> withCheckDigit(("2" + digits(6, random)).take(7))
            CodeFormat.UPCA -> withCheckDigit(("20" + digits(9, random)).take(11))
        }

    /**
     * True when a decoded [text] of [format] solves the mission with [expected].
     *
     * With no expectation any well-formed code counts, so a broken checksum is rejected. With an
     * expectation the payload is whatever the user bound — a product barcode or an arbitrary QR
     * text — so it is compared as text instead of being re-validated.
     */
    fun accepts(expected: String?, text: String, format: CodeFormat): Boolean =
        if (expected.isNullOrBlank()) isValid(text, format) else matches(expected, text)

    /** True when [scanned] satisfies [expected]; an empty expectation accepts anything. */
    fun matches(expected: String, scanned: String): Boolean {
        if (expected.isEmpty()) return true
        val left = normalize(expected)
        val right = normalize(scanned)
        if (left == right) return true
        // ZXing reports a UPC-A code as EAN-13 with a leading zero; that is the same barcode.
        val leftDigits = left.trimStart('0')
        return leftDigits.isNotEmpty() && leftDigits == right.trimStart('0')
    }

    fun normalize(value: String): String = value.trim().lowercase()

    /** Check digit for the body of an EAN/UPC code. */
    fun checkDigit(body: String): Int {
        var sum = 0
        var weight = 3
        for (index in body.indices.reversed()) {
            val digit = body[index] - '0'
            if (digit !in 0..9) return -1
            sum += digit * weight
            weight = if (weight == 3) 1 else 3
        }
        return (10 - sum % 10) % 10
    }

    fun isValid(code: String, format: CodeFormat): Boolean {
        if (!format.numeric) return code.isNotEmpty()
        if (code.length != format.length) return false
        if (code.any { it !in '0'..'9' }) return false
        return checkDigit(code.dropLast(1)) == code.last() - '0'
    }

    fun withCheckDigit(body: String): String = body + checkDigit(body).toString()

    /**
     * Payload to keep when the user switches the code format: a code the new format cannot encode
     * (letters in EAN, wrong length) would render nothing, so a fresh one is generated instead.
     */
    fun ensureEncodable(payload: String, format: CodeFormat): String =
        if (payload.isEmpty() || isValid(payload, format)) payload else generate(format)

    private fun token(random: Random): String =
        PREFIX + (1..BODY_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    private fun digits(count: Int, random: Random): String =
        (1..count).map { random.nextInt(10).toString() }.joinToString("")
}

internal fun JsonObject.with(key: String, value: String): JsonObject =
    JsonObject(this + (key to JsonPrimitive(value)))