package io.github.caendeith.awakr.ui.format

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Parses the opaque mission config blob, falling back to an empty object on garbage. */
fun parseMissionConfig(raw: String): JsonObject =
    runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
