package io.github.caendeith.awakr.plugins.mission.qr

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.random.Random

/**
 * The printed code an alarm is bound to.
 *
 * An empty payload keeps the "any code will do" behaviour, which is useful when the user has no
 * printer and simply points the camera at the barcode on the shampoo bottle.
 */
internal object QrPayload {

    const val KEY_PAYLOAD = "payload"

    private const val PREFIX = "AWAKR-"
    private const val BODY_LENGTH = 8

    /** No 0/O/1/I so a code read out loud stays unambiguous. */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun expected(config: JsonObject): String =
        (config[KEY_PAYLOAD] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()

    fun generate(random: Random = Random.Default): String =
        PREFIX + (1..BODY_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** Blank [expected] accepts any decodable code. */
    fun matches(expected: String, scanned: String): Boolean {
        val wanted = expected.trim()
        if (wanted.isEmpty()) return true
        return wanted.equals(scanned.trim(), ignoreCase = true)
    }
}
