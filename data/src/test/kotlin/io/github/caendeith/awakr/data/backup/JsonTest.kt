package io.github.caendeith.awakr.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonTest {

    @Test
    fun `writer escapes characters a JSON string cannot hold`() {
        val value = "a\"b\\c\nd\te\u0001é🚀"
        val text = JsonWriter.write(JsonValue.Obj(linkedMapOf("label" to JsonValue.Str(value))))
        assertEquals("{\n  \"label\": \"a\\\"b\\\\c\\nd\\te\\u0001é🚀\"\n}", text)
        val parsed = JsonParser.parse(text) as JsonValue.Obj
        assertEquals(value, parsed.string("label"))
    }

    @Test
    fun `numbers keep their exact text`() {
        val parsed = JsonParser.parse("""{"epoch": 1762000000001, "small": -3, "ratio": 1.5}""") as JsonValue.Obj
        assertEquals(1762000000001L, parsed.long("epoch"))
        assertEquals(-3, parsed.int("small"))
        assertEquals("1.5", (parsed.fields["ratio"] as JsonValue.Num).raw)
    }

    @Test
    fun `nested objects and arrays survive a round trip`() {
        val tree = jsonObject(
            "alarms" to jsonArray(
                listOf(
                    jsonObject("hour" to intValue(7), "enabled" to boolValue(true)),
                    jsonObject("hour" to intValue(9), "sound" to stringOrNull(null)),
                ),
            ),
        )
        val reparsed = JsonParser.parse(JsonWriter.write(tree))
        assertEquals(tree, reparsed)
    }

    @Test
    fun `empty containers are written compactly`() {
        assertEquals("{}", JsonWriter.write(JsonValue.Obj()))
        assertEquals("[]", JsonWriter.write(JsonValue.Arr()))
    }

    @Test
    fun `broken json is rejected`() {
        assertThrows(JsonParseException::class.java) { JsonParser.parse("{\"a\": }") }
        assertThrows(JsonParseException::class.java) { JsonParser.parse("") }
        assertThrows(JsonParseException::class.java) { JsonParser.parse("{} trailing") }
        assertThrows(JsonParseException::class.java) { JsonParser.parse("{\"a\" 1}") }
    }

    @Test
    fun `field of the wrong type reads as absent`() {
        val parsed = JsonParser.parse("""{"hour": "seven", "enabled": "yes"}""") as JsonValue.Obj
        assertEquals(null, parsed.int("hour"))
        assertEquals(null, parsed.bool("enabled"))
    }
}
