package io.github.caendeith.awakr.data.backup

/**
 * Minimal JSON support for the backup format.
 *
 * `:data` does not depend on kotlinx.serialization (adding a dependency is out of scope for
 * this feature), so the handful of values a backup file needs are written and parsed here.
 * The tree is deliberately small: objects, arrays, strings, numbers, booleans and null.
 *
 * Numbers keep their literal text, so epoch millis (Long) survive a round trip exactly
 * instead of being pushed through a Double.
 */
sealed interface JsonValue {
    data class Obj(val fields: Map<String, JsonValue> = emptyMap()) : JsonValue
    data class Arr(val items: List<JsonValue> = emptyList()) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val raw: String) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object JsonNull : JsonValue
}

/** Builds an object that keeps field order, so exported files stay stable and diff-friendly. */
fun jsonObject(vararg fields: Pair<String, JsonValue>): JsonValue.Obj =
    JsonValue.Obj(linkedMapOf(*fields))

fun jsonArray(items: List<JsonValue>): JsonValue.Arr = JsonValue.Arr(items)

fun stringOrNull(value: String?): JsonValue =
    if (value == null) JsonValue.JsonNull else JsonValue.Str(value)

fun longOrNull(value: Long?): JsonValue =
    if (value == null) JsonValue.JsonNull else JsonValue.Num(value.toString())

fun intValue(value: Int): JsonValue = JsonValue.Num(value.toString())

fun longValue(value: Long): JsonValue = JsonValue.Num(value.toString())

fun boolValue(value: Boolean): JsonValue = JsonValue.Bool(value)

/** Typed, tolerant accessors: a field of the wrong type reads as absent. */
fun JsonValue.Obj.string(name: String): String? = (fields[name] as? JsonValue.Str)?.value

fun JsonValue.Obj.long(name: String): Long? = (fields[name] as? JsonValue.Num)?.raw?.toLongOrNull()

fun JsonValue.Obj.int(name: String): Int? = long(name)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

fun JsonValue.Obj.bool(name: String): Boolean? = (fields[name] as? JsonValue.Bool)?.value

fun JsonValue.Obj.obj(name: String): JsonValue.Obj? = fields[name] as? JsonValue.Obj

fun JsonValue.Obj.array(name: String): JsonValue.Arr? = fields[name] as? JsonValue.Arr

/** Serialises a tree to pretty-printed JSON with two-space indentation. */
object JsonWriter {

    fun write(value: JsonValue): String = StringBuilder().apply { appendValue(value, 0) }.toString()

    private fun StringBuilder.appendValue(value: JsonValue, depth: Int) {
        when (value) {
            is JsonValue.Obj -> appendObject(value, depth)
            is JsonValue.Arr -> appendArray(value, depth)
            is JsonValue.Str -> appendQuoted(value.value)
            is JsonValue.Num -> append(value.raw)
            is JsonValue.Bool -> append(if (value.value) "true" else "false")
            JsonValue.JsonNull -> append("null")
        }
    }

    private fun StringBuilder.appendObject(value: JsonValue.Obj, depth: Int) {
        if (value.fields.isEmpty()) {
            append("{}")
            return
        }
        append("{\n")
        val entries = value.fields.entries.toList()
        entries.forEachIndexed { index, entry ->
            appendIndent(depth + 1)
            appendQuoted(entry.key)
            append(": ")
            appendValue(entry.value, depth + 1)
            if (index != entries.lastIndex) append(',')
            append('\n')
        }
        appendIndent(depth)
        append('}')
    }

    private fun StringBuilder.appendArray(value: JsonValue.Arr, depth: Int) {
        if (value.items.isEmpty()) {
            append("[]")
            return
        }
        append("[\n")
        value.items.forEachIndexed { index, item ->
            appendIndent(depth + 1)
            appendValue(item, depth + 1)
            if (index != value.items.lastIndex) append(',')
            append('\n')
        }
        appendIndent(depth)
        append(']')
    }

    private fun StringBuilder.appendIndent(depth: Int) {
        repeat(depth) { append("  ") }
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (ch < ' ') {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
    }
}

/** Raised when the text is not valid JSON; the offset points at the offending character. */
class JsonParseException(message: String, val offset: Int) : IllegalArgumentException("$message at offset $offset")

/** Recursive-descent parser for the JSON subset above. */
object JsonParser {

    private const val MAX_DEPTH = 64

    fun parse(text: String): JsonValue {
        val reader = Reader(text)
        reader.skipWhitespace()
        val value = reader.readValue(0)
        reader.skipWhitespace()
        if (!reader.end) throw JsonParseException("Unexpected trailing data", reader.index)
        return value
    }

    private class Reader(private val text: String) {

        var index = 0
            private set

        val end: Boolean get() = index >= text.length

        fun skipWhitespace() {
            while (!end) {
                when (text[index]) {
                    ' ', '\t', '\n', '\r' -> index++
                    else -> return
                }
            }
        }

        private fun peek(): Char {
            if (end) throw JsonParseException("Unexpected end of input", index)
            return text[index]
        }

        fun readValue(depth: Int): JsonValue {
            if (depth > MAX_DEPTH) throw JsonParseException("Nesting is too deep", index)
            return when (val ch = peek()) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> JsonValue.Str(readString())
                't' -> readLiteral("true", JsonValue.Bool(true))
                'f' -> readLiteral("false", JsonValue.Bool(false))
                'n' -> readLiteral("null", JsonValue.JsonNull)
                else -> if (ch == '-' || ch.isDigit()) readNumber() else {
                    throw JsonParseException("Unexpected character '$ch'", index)
                }
            }
        }

        private fun readLiteral(literal: String, value: JsonValue): JsonValue {
            if (!text.startsWith(literal, index)) {
                throw JsonParseException("Expected '$literal'", index)
            }
            index += literal.length
            return value
        }

        private fun readObject(depth: Int): JsonValue {
            expect('{')
            skipWhitespace()
            val fields = linkedMapOf<String, JsonValue>()
            if (peek() == '}') {
                index++
                return JsonValue.Obj(fields)
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw JsonParseException("Expected a field name", index)
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                fields[key] = readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return JsonValue.Obj(fields)
                    }
                    else -> throw JsonParseException("Expected ',' or '}'", index)
                }
            }
        }

        private fun readArray(depth: Int): JsonValue {
            expect('[')
            skipWhitespace()
            val items = mutableListOf<JsonValue>()
            if (peek() == ']') {
                index++
                return JsonValue.Arr(items)
            }
            while (true) {
                skipWhitespace()
                items += readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return JsonValue.Arr(items)
                    }
                    else -> throw JsonParseException("Expected ',' or ']'", index)
                }
            }
        }

        private fun expect(ch: Char) {
            if (peek() != ch) throw JsonParseException("Expected '$ch'", index)
            index++
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                val ch = peek()
                index++
                when {
                    ch == '"' -> return builder.toString()
                    ch == '\\' -> builder.append(readEscape())
                    ch < ' ' -> throw JsonParseException("Unescaped control character", index - 1)
                    else -> builder.append(ch)
                }
            }
        }

        private fun readEscape(): Char {
            if (end) throw JsonParseException("Unterminated escape", index)
            val ch = text[index++]
            return when (ch) {
                '"', '\\', '/' -> ch
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> readUnicodeEscape()
                else -> throw JsonParseException("Invalid escape '\\$ch'", index - 1)
            }
        }

        private fun readUnicodeEscape(): Char {
            if (index + 4 > text.length) throw JsonParseException("Truncated \\u escape", index)
            val hex = text.substring(index, index + 4)
            index += 4
            return hex.toIntOrNull(16)?.toChar()
                ?: throw JsonParseException("Invalid \\u escape '$hex'", index - 4)
        }

        private fun readNumber(): JsonValue {
            val start = index
            if (peek() == '-') index++
            consumeDigits()
            if (!end && text[index] == '.') {
                index++
                consumeDigits()
            }
            if (!end && (text[index] == 'e' || text[index] == 'E')) {
                index++
                if (!end && (text[index] == '+' || text[index] == '-')) index++
                consumeDigits()
            }
            val raw = text.substring(start, index)
            if (raw.toDoubleOrNull() == null) throw JsonParseException("Invalid number '$raw'", start)
            return JsonValue.Num(raw)
        }

        private fun consumeDigits() {
            while (!end && text[index].isDigit()) index++
        }
    }
}
